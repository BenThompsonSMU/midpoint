/*
 * Copyright (c) 2010-2018 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evolveum.midpoint.repo.cache;

import com.evolveum.midpoint.prism.Containerable;
import com.evolveum.midpoint.prism.PrismObject;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.match.MatchingRuleRegistry;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.repo.api.*;
import com.evolveum.midpoint.repo.api.perf.PerformanceMonitor;
import com.evolveum.midpoint.repo.api.query.ObjectFilterExpressionEvaluator;
import com.evolveum.midpoint.schema.*;
import com.evolveum.midpoint.schema.cache.CacheConfigurationManager;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.DiagnosticContextHolder;
import com.evolveum.midpoint.util.caching.CacheConfiguration;
import com.evolveum.midpoint.util.caching.CacheConfiguration.CacheObjectTypeConfiguration;
import com.evolveum.midpoint.util.caching.CachePerformanceCollector;
import com.evolveum.midpoint.util.caching.CacheUtil;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;
import java.util.Objects;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.evolveum.midpoint.schema.cache.CacheType.*;

/**
 * Read-through write-through per-session repository cache.
 *
 * TODO doc
 * TODO logging perf measurements
 *
 * @author Radovan Semancik
 *
 */
@Component(value="cacheRepositoryService")
public class RepositoryCache implements RepositoryService {

	private static final Trace LOGGER = TraceManager.getTrace(RepositoryCache.class);
	private static final Trace PERFORMANCE_ADVISOR = TraceManager.getPerformanceAdvisorTrace();

	private static final ThreadLocal<LocalObjectCache> localObjectCacheInstance = new ThreadLocal<>();
	private static final ThreadLocal<LocalVersionCache> localVersionCacheInstance = new ThreadLocal<>();
	private static final ThreadLocal<LocalQueryCache> localQueryCacheInstance = new ThreadLocal<>();

	@Autowired private RepositoryService repositoryService;
	@Autowired private CacheDispatcher cacheDispatcher;
	@Autowired private MatchingRuleRegistry matchingRuleRegistry;
	@Autowired private GlobalQueryCache globalQueryCache;
	@Autowired private GlobalObjectCache globalObjectCache;
	@Autowired private CacheConfigurationManager cacheConfigurationManager;

	private static final Random RND = new Random();

	private Integer modifyRandomDelayRange;

	public RepositoryCache() {
    }

	private static LocalObjectCache getLocalObjectCache() {
		return localObjectCacheInstance.get();
	}

	private static LocalVersionCache getLocalVersionCache() {
		return localVersionCacheInstance.get();
	}

	private static LocalQueryCache getLocalQueryCache() {
		return localQueryCacheInstance.get();
	}

	public static void init() {
	}

	public static void destroy() {
		LocalObjectCache.destroy(localObjectCacheInstance, LOGGER);
		LocalVersionCache.destroy(localVersionCacheInstance, LOGGER);
		LocalQueryCache.destroy(localQueryCacheInstance, LOGGER);
	}

	public static void enter(CacheConfigurationManager mgr) {
		// let's compute configuration first -- an exception can be thrown there; so if it happens, none of the caches
		// will be entered into upon exit of this method
		CacheConfiguration objectCacheConfig = mgr.getConfiguration(LOCAL_REPO_OBJECT_CACHE);
		CacheConfiguration versionCacheConfig = mgr.getConfiguration(LOCAL_REPO_VERSION_CACHE);
		CacheConfiguration queryCacheConfig = mgr.getConfiguration(LOCAL_REPO_QUERY_CACHE);

		LocalObjectCache.enter(localObjectCacheInstance, LocalObjectCache.class, objectCacheConfig, LOGGER);
		LocalVersionCache.enter(localVersionCacheInstance, LocalVersionCache.class, versionCacheConfig, LOGGER);
		LocalQueryCache.enter(localQueryCacheInstance, LocalQueryCache.class, queryCacheConfig, LOGGER);
	}

	public static void exit() {
		LocalObjectCache.exit(localObjectCacheInstance, LOGGER);
		LocalVersionCache.exit(localVersionCacheInstance, LOGGER);
		LocalQueryCache.exit(localQueryCacheInstance, LOGGER);
	}

	public static boolean exists() {
		return LocalObjectCache.exists(localObjectCacheInstance) || LocalVersionCache.exists(localVersionCacheInstance) || LocalQueryCache
				.exists(localQueryCacheInstance);
	}

	@SuppressWarnings("unused")
	public Integer getModifyRandomDelayRange() {
		return modifyRandomDelayRange;
	}

	public void setModifyRandomDelayRange(Integer modifyRandomDelayRange) {
		this.modifyRandomDelayRange = modifyRandomDelayRange;
	}

	public static String debugDump() {
		// TODO
		return LocalObjectCache.debugDump(localObjectCacheInstance) + "\n" +
				LocalVersionCache.debugDump(localVersionCacheInstance) + "\n" +
				LocalQueryCache.debugDump(localQueryCacheInstance);
	}

	@Override
	public <T extends ObjectType> PrismObject<T> getObject(Class<T> type, String oid,
			Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult) throws ObjectNotFoundException, SchemaException {

		CachePerformanceCollector collector = CachePerformanceCollector.INSTANCE;
		LocalObjectCache localObjectsCache = getLocalObjectCache();

		Context global = new Context(globalObjectCache.getConfiguration(), type);
		Context local = localObjectsCache != null ?
				new Context(localObjectsCache.getConfiguration(), type) :
				new Context(cacheConfigurationManager.getConfiguration(LOCAL_REPO_OBJECT_CACHE), type);

		/*
		 * Checks related to both caches
		 */

		PassReason passReason = getPassReason(options, type);
		if (passReason != null) {
			// local nor global cache not interested in caching this object
			if (localObjectsCache != null) {
				localObjectsCache.registerPass();
			}
			collector.registerPass(LocalObjectCache.class, type, local.statisticsLevel);
			collector.registerPass(GlobalObjectCache.class, type, global.statisticsLevel);
			log("Cache (local/global): PASS:{} getObject {} ({}, {})",  local.tracePass || global.tracePass, passReason, oid, type.getSimpleName(), options);

			return getObjectInternal(type, oid, options, parentResult);
		}

		/*
		 * Let's try local cache
		 */
		boolean readOnly = GetOperationOptions.isReadOnly(SelectorOptions.findRootOptions(options));

		if (localObjectsCache == null) {
			log("Cache (local): NULL getObject {} ({})", false, oid, type.getSimpleName());
			registerNotAvailable(LocalObjectCache.class, type, local.statisticsLevel);
		} else {
			//noinspection unchecked
			PrismObject<T> object = (PrismObject) (local.supports ? localObjectsCache.get(oid) : null);
			if (object != null) {
				localObjectsCache.registerHit();
				collector.registerHit(LocalObjectCache.class, type, local.statisticsLevel);
				log("Cache (local): HIT {} getObject {} ({})", false, readOnly ? "" : "(clone)", oid, type.getSimpleName());
				return cloneIfNecessary(object, readOnly);
			}
			if (local.supports) {
				localObjectsCache.registerMiss();
				collector.registerMiss(LocalObjectCache.class, type, local.statisticsLevel);
				log("Cache (local): MISS {} getObject ({})", local.traceMiss, oid, type.getSimpleName());
			} else {
				localObjectsCache.registerPass();
				collector.registerPass(LocalObjectCache.class, type, local.statisticsLevel);
				log("Cache (local): PASS:CONFIGURATION {} getObject ({})", local.tracePass, oid, type.getSimpleName());
			}
		}

		/*
		 * Then try global cache
		 */

		if (!globalObjectCache.isAvailable()) {
			collector.registerNotAvailable(GlobalObjectCache.class, type, global.statisticsLevel);
			log("Cache (global): NOT_AVAILABLE {} getObject ({})", false, oid, type.getSimpleName());
			PrismObject<T> object = getObjectInternal(type, oid, options, parentResult);
			locallyCacheObject(localObjectsCache, local.supports, object, readOnly);
			return object;
		} else if (!global.supports) {
			// caller is not interested in cached value, or global cache doesn't want to cache value
			collector.registerPass(GlobalObjectCache.class, type, global.statisticsLevel);
			log("Cache (local): PASS:CONFIGURATION {} getObject ({})", global.tracePass, oid, type.getSimpleName());
			PrismObject<T> object = getObjectInternal(type, oid, options, parentResult);
			locallyCacheObject(localObjectsCache, local.supports, object, readOnly);
			return object;
		}

		assert global.cacheConfig != null && global.typeConfig != null;

		PrismObject<T> object;
		GlobalCacheObjectValue<T> cacheObject = globalObjectCache.get(oid);
		if (cacheObject == null) {
			collector.registerMiss(GlobalObjectCache.class, type, global.statisticsLevel);
			log("Cache (global): MISS getObject {}", global.traceMiss, oid);
			object = loadAndCacheObject(type, oid, options, readOnly, localObjectsCache, local.supports, parentResult);
		} else {
			if (!shouldCheckVersion(cacheObject)) {
				collector.registerHit(GlobalObjectCache.class, type, global.statisticsLevel);
				log("Cache (global): HIT getObject {}", false, oid);
				object = cacheObject.getObject();
				locallyCacheObjectWithoutCloning(localObjectsCache, local.supports, object);
				object = cloneIfNecessary(object, readOnly);
			} else {
				if (hasVersionChanged(type, oid, cacheObject, parentResult)) {
					collector.registerMiss(GlobalObjectCache.class, type, global.statisticsLevel);
					log("Cache (global): MISS because of version changed - getObject {}:{}", global.traceMiss, type, oid);
					object = loadAndCacheObject(type, oid, options, readOnly, localObjectsCache, local.supports, parentResult);
				} else {
					cacheObject.setTimeToLive(System.currentTimeMillis() + getTimeToVersionCheck(global.typeConfig, global.cacheConfig));    // version matches, renew ttl
					collector.registerWeakHit(GlobalObjectCache.class, type, global.statisticsLevel);
					log("Cache (global): HIT with version check - getObject {}: {}", global.traceMiss, type, oid);
					object = cacheObject.getObject();
					locallyCacheObjectWithoutCloning(localObjectsCache, local.supports, object);
					object = cloneIfNecessary(object, readOnly);
				}
			}
		}
        return object;
	}

	private long getTimeToVersionCheck(@NotNull CacheObjectTypeConfiguration typeConfig, @NotNull CacheConfiguration cacheConfig) {
		if (typeConfig.getEffectiveTimeToVersionCheck() != null) {
			return typeConfig.getEffectiveTimeToVersionCheck() * 1000L;
		} else if (typeConfig.getEffectiveTimeToLive() != null) {
			return typeConfig.getEffectiveTimeToLive() * 1000L;
		} else if (cacheConfig.getTimeToLive() != null) {
			return cacheConfig.getTimeToLive() * 1000L;
		} else {
			return GlobalObjectCache.DEFAULT_TIME_TO_LIVE * 1000L;
		}
	}

	private void registerNotAvailable(Class<?> cacheClass, Class<?> type, CacheConfiguration.StatisticsLevel statisticsLevel) {
		CachePerformanceCollector.INSTANCE.registerNotAvailable(cacheClass, type, statisticsLevel);
	}

	private <T extends ObjectType> PrismObject<T> cloneIfNecessary(PrismObject<T> object, boolean readOnly) {
		if (readOnly) {
			return object;
		} else {
			// if client requested writable object, we need to provide him with a copy
			return object.clone();
		}
	}

	private <T extends ObjectType> PrismObject<T> getObjectInternal(Class<T> type, String oid, Collection<SelectorOptions<GetOperationOptions>> options,
			OperationResult parentResult) throws SchemaException, ObjectNotFoundException {
		Long startTime = repoOpStart();
		try {
			return repositoryService.getObject(type, oid, options, parentResult);
		} finally {
			repoOpEnd(startTime);
		}
	}

	private Long repoOpStart() {
		RepositoryPerformanceMonitor monitor = DiagnosticContextHolder.get(RepositoryPerformanceMonitor.class);
		if (monitor == null) {
			return null;
		} else {
			return System.currentTimeMillis();
		}
	}
	
	private void repoOpEnd(Long startTime) {
		RepositoryPerformanceMonitor monitor = DiagnosticContextHolder.get(RepositoryPerformanceMonitor.class);
		if (monitor != null) {
			monitor.recordRepoOperation(System.currentTimeMillis() - startTime);
		}
	}

	/*
	 * Tasks are usually rapidly changing.
	 *
	 * Cases are perhaps not changing that rapidly but these are objects that are used for communication of various parties;
	 * so - to avoid having stale data - we skip caching them altogether.
	 */
	private boolean alwaysNotCacheable(Class<?> type) {
		return type.equals(TaskType.class) || type.equals(CaseType.class);
	}

	@Override
	public <T extends ObjectType> String addObject(PrismObject<T> object, RepoAddOptions options, OperationResult parentResult)
			throws ObjectAlreadyExistsException, SchemaException {
		String oid;
		Long startTime = repoOpStart();
		try {
			oid = repositoryService.addObject(object, options, parentResult);
		} finally {
			repoOpEnd(startTime);
		}
		// DON't cache the object here. The object may not have proper "JAXB" form, e.g. some pieces may be
		// DOM element instead of JAXB elements. Not to cache it is safer and the performance loss
		// is acceptable.
		if (options != null && options.isOverwrite()) {
			invalidateCacheEntries(object.getCompileTimeClass(), oid,
					new ModifyObjectResult<>(object.getUserData(RepositoryService.KEY_ORIGINAL_OBJECT), object, Collections.emptyList()));
		} else {
			// just for sure (the object should not be there but ...)
			invalidateCacheEntries(object.getCompileTimeClass(), oid, new AddObjectResult<>(object));
		}
		return oid;
	}

	@NotNull
	@Override
	public <T extends ObjectType> SearchResultList<PrismObject<T>> searchObjects(Class<T> type, ObjectQuery query,
			Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult) throws SchemaException {

		CachePerformanceCollector collector = CachePerformanceCollector.INSTANCE;

		LocalQueryCache localQueryCache = getLocalQueryCache();

		Context global = new Context(globalQueryCache.getConfiguration(), type);
		Context local = localQueryCache != null ?
				new Context(localQueryCache.getConfiguration(), type) :
				new Context(cacheConfigurationManager.getConfiguration(LOCAL_REPO_QUERY_CACHE), type);

		/*
		 * Checks related to both caches
		 */

		PassReason passReason = getPassReason(options, type);
		if (passReason != null) {
			if (localQueryCache != null) {
				localQueryCache.registerPass();
			}
			collector.registerPass(LocalQueryCache.class, type, local.statisticsLevel);
			collector.registerPass(GlobalQueryCache.class, type, global.statisticsLevel);
			log("Cache (local/global): PASS:{} searchObjects ({}, {})", local.tracePass || global.tracePass, passReason, type.getSimpleName(), options);
			return searchObjectsInternal(type, query, options, parentResult);
		}
		QueryKey key = new QueryKey(type, query);

		/*
		 * Let's try local cache
		 */

		boolean readOnly = GetOperationOptions.isReadOnly(SelectorOptions.findRootOptions(options));

		if (localQueryCache == null) {
			log("Cache (local): NULL searchObjects ({})", false, type.getSimpleName());
			registerNotAvailable(LocalQueryCache.class, type, local.statisticsLevel);
		} else {
			SearchResultList queryResult = local.supports ? localQueryCache.get(key) : null;
			if (queryResult != null) {
				localQueryCache.registerHit();
				collector.registerHit(LocalQueryCache.class, type, local.statisticsLevel);
				if (readOnly) {
					log("Cache: HIT searchObjects {} ({})", false, query, type.getSimpleName());
					//noinspection unchecked
					return queryResult;
				} else {
					log("Cache: HIT(clone) searchObjects {} ({})", false, query, type.getSimpleName());
					//noinspection unchecked
					return queryResult.clone();
				}
			}
			if (local.supports) {
				localQueryCache.registerMiss();
				collector.registerMiss(LocalQueryCache.class, type, local.statisticsLevel);
				log("Cache: MISS searchObjects {} ({})", local.traceMiss, query, type.getSimpleName());
			} else {
				localQueryCache.registerPass();
				collector.registerPass(LocalQueryCache.class, type, local.statisticsLevel);
				log("Cache: PASS:CONFIGURATION searchObjects {} ({})", local.tracePass, query, type.getSimpleName());
			}
		}

		/*
		 * Then try global cache
		 */
		if (!globalQueryCache.isAvailable()) {
			collector.registerNotAvailable(GlobalQueryCache.class, type, global.statisticsLevel);
			log("Cache (global): NOT_AVAILABLE {} searchObjects ({})", false, query, type.getSimpleName());
			SearchResultList<PrismObject<T>> objects = searchObjectsInternal(type, query, options, parentResult);
			locallyCacheSearchResult(localQueryCache, local.supports, key, readOnly, objects);
			return objects;
		} else if (!global.supports) {
			// caller is not interested in cached value, or global cache doesn't want to cache value
			collector.registerPass(GlobalQueryCache.class, type, global.statisticsLevel);
			log("Cache (global): PASS:CONFIGURATION {} searchObjects ({})", global.tracePass, query, type.getSimpleName());
			SearchResultList<PrismObject<T>> objects = searchObjectsInternal(type, query, options, parentResult);
			locallyCacheSearchResult(localQueryCache, local.supports, key, readOnly, objects);
			return objects;
		}

		assert global.cacheConfig != null && global.typeConfig != null;

		SearchResultList<PrismObject<T>> searchResult = globalQueryCache.get(key);

		if (searchResult == null) {
			collector.registerMiss(GlobalQueryCache.class, type, global.statisticsLevel);
			log("Cache (global): MISS searchObjects {}", global.traceMiss, key);
			searchResult = executeAndCacheSearch(key, options, readOnly, localQueryCache, local.supports, parentResult);
		} else {
			collector.registerHit(GlobalQueryCache.class, type, global.statisticsLevel);
			log("Cache (global): HIT searchObjects {}", false, key);
			locallyCacheSearchResult(localQueryCache, local.supports, key, readOnly, searchResult);
		}
		return searchResult;
	}

	private <T extends ObjectType> void locallyCacheSearchResult(LocalQueryCache cache, boolean supports, QueryKey key,
			boolean readOnly, SearchResultList<PrismObject<T>> objects) {
		// TODO optimize cloning
		if (cache != null && supports) {
			cache.put(key, objects.clone());
		}
		LocalObjectCache localObjectCache = getLocalObjectCache();
		if (localObjectCache != null) {
			for (PrismObject<T> object : objects) {
				Class<? extends ObjectType> type = object.asObjectable().getClass();
				if (localObjectCache.supportsObjectType(type)) {
					locallyCacheObject(localObjectCache, true, object, readOnly);
				}
			}
		}
	}

	private <T extends ObjectType> void globallyCacheSearchResult(QueryKey key, boolean readOnly,
			SearchResultList<PrismObject<T>> objects) {
		SearchResultList<PrismObject<T>> cloned = objects.clone();
		for (PrismObject<T> object : cloned) {
			globallyCacheObjectWithoutCloning(object);
		}
		globalQueryCache.put(key, cloned);
	}

	@NotNull
	private <T extends ObjectType> SearchResultList<PrismObject<T>> searchObjectsInternal(Class<T> type, ObjectQuery query,
			Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult)
			throws SchemaException {
		Long startTime = repoOpStart();
		try {
			return repositoryService.searchObjects(type, query, options, parentResult);
		} finally {
			repoOpEnd(startTime);
		}
	}

	@Override
	public <T extends Containerable> SearchResultList<T> searchContainers(Class<T> type, ObjectQuery query, Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult) throws SchemaException {
		Long startTime = repoOpStart();
		try {
			return repositoryService.searchContainers(type, query, options, parentResult);
		} finally {
			repoOpEnd(startTime);
		}
	}

	/* (non-Javadoc)
	 * @see com.evolveum.midpoint.repo.api.RepositoryService#searchObjectsIterative(java.lang.Class, com.evolveum.midpoint.prism.query.ObjectQuery, com.evolveum.midpoint.schema.ResultHandler, com.evolveum.midpoint.schema.result.OperationResult)
	 */
	@Override
	public <T extends ObjectType> SearchResultMetadata searchObjectsIterative(Class<T> type, ObjectQuery query,
			final ResultHandler<T> handler, final Collection<SelectorOptions<GetOperationOptions>> options,
			boolean strictlySequential, OperationResult parentResult) throws SchemaException {
		LocalQueryCache localQueryCache = getLocalQueryCache();
		LocalObjectCache localObjectCache = getLocalObjectCache();
		Context globalQ = new Context(globalQueryCache.getConfiguration(), type);
		Context localQ = localQueryCache != null ?
				new Context(localQueryCache.getConfiguration(), type) :
				new Context(cacheConfigurationManager.getConfiguration(LOCAL_REPO_QUERY_CACHE), type);

		boolean readOnly = GetOperationOptions.isReadOnly(SelectorOptions.findRootOptions(options));
		// TODO how exactly to record this?
		if (localQueryCache != null) {
			localQueryCache.registerPass();
			CachePerformanceCollector.INSTANCE.registerPass(LocalQueryCache.class, type, localQ.statisticsLevel);
		} else {
			CachePerformanceCollector.INSTANCE.registerPass(GlobalQueryCache.class, type, globalQ.statisticsLevel);
		}
		log("Cache: PASS (local/global) searchObjectsIterative ({})", localQ.tracePass || globalQ.tracePass, type.getSimpleName());
		ResultHandler<T> myHandler = (object, parentResult1) -> {
			cacheLoadedObject(object, readOnly, localObjectCache);
			return handler.handle(object, parentResult1);
		};
		Long startTime = repoOpStart();
		try {
			return repositoryService.searchObjectsIterative(type, query, myHandler, options, strictlySequential, parentResult);
		} finally {
			repoOpEnd(startTime);
		}
	}

	private <T extends ObjectType> void cacheLoadedObject(PrismObject<T> object, boolean readOnly,
			LocalObjectCache localObjectCache) {
		Class<? extends ObjectType> objectType = object.asObjectable().getClass();
		boolean putIntoLocal = localObjectCache != null && localObjectCache.supportsObjectType(objectType);
		boolean putIntoGlobal = globalObjectCache.isAvailable() && globalObjectCache.supportsObjectType(objectType);
		if (putIntoLocal || putIntoGlobal) {
			PrismObject<T> objectToCache = prepareObjectToCache(object, readOnly);
			if (putIntoLocal) {
				locallyCacheObjectWithoutCloning(localObjectCache, true, objectToCache);
			}
			if (putIntoGlobal) {
				globallyCacheObjectWithoutCloning(objectToCache);
			}
		}
	}

	@Deprecated
	@Override
	public <T extends ObjectType> int countObjects(Class<T> type, ObjectQuery query, OperationResult parentResult)
			throws SchemaException {
		// TODO use cached query result if applicable
		log("Cache: PASS countObjects ({})", false, type.getSimpleName());
		Long startTime = repoOpStart();
		try {
			return repositoryService.countObjects(type, query, null, parentResult);
		} finally {
			repoOpEnd(startTime);
		}
	}

	@Override
	public <T extends Containerable> int countContainers(Class<T> type, ObjectQuery query,
			Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult) {
		log("Cache: PASS countContainers ({})", false, type.getSimpleName());
		Long startTime = repoOpStart();
		try {
			return repositoryService.countContainers(type, query, options, parentResult);
		} finally {
			repoOpEnd(startTime);
		}
	}

	@Override
	public <T extends ObjectType> int countObjects(Class<T> type, ObjectQuery query,
			Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult)
			throws SchemaException {
		// TODO use cached query result if applicable
		log("Cache: PASS countObjects ({})", false, type.getSimpleName());
		Long startTime = repoOpStart();
		try {
			return repositoryService.countObjects(type, query, options, parentResult);
		} finally {
			repoOpEnd(startTime);
		}
	}

	@NotNull
	public <T extends ObjectType> ModifyObjectResult<T> modifyObject(Class<T> type, String oid, Collection<? extends ItemDelta> modifications,
													OperationResult parentResult) throws ObjectNotFoundException, SchemaException, ObjectAlreadyExistsException {
		return modifyObject(type, oid, modifications, null, parentResult);
	}

	@NotNull
	@Override
	public <T extends ObjectType> ModifyObjectResult<T> modifyObject(Class<T> type, String oid, Collection<? extends ItemDelta> modifications,
			RepoModifyOptions options, OperationResult parentResult) throws ObjectNotFoundException, SchemaException, ObjectAlreadyExistsException {
		try {
			return modifyObject(type, oid, modifications, null, options, parentResult);
		} catch (PreconditionViolationException e) {
			throw new AssertionError(e);
		}
	}

	private void delay(Integer delayRange) {
		if (delayRange == null) {
			return;
		}
		int delay = RND.nextInt(delayRange);
		try {
			Thread.sleep(delay);
		} catch (InterruptedException e) {
			// Nothing to do
		}
	}

	@NotNull
	@Override
	public <T extends ObjectType> ModifyObjectResult<T> modifyObject(Class<T> type, String oid, Collection<? extends ItemDelta> modifications,
			ModificationPrecondition<T> precondition, RepoModifyOptions options, OperationResult parentResult)
			throws ObjectNotFoundException, SchemaException, ObjectAlreadyExistsException, PreconditionViolationException {
		delay(modifyRandomDelayRange);
		Long startTime = repoOpStart();
		ModifyObjectResult<T> modifyInfo = null;
		try {
			modifyInfo = repositoryService.modifyObject(type, oid, modifications, precondition, options, parentResult);
			return modifyInfo;
		} finally {
			repoOpEnd(startTime);
			// this changes the object. We are too lazy to apply changes ourselves, so just invalidate
			// the object in cache
			invalidateCacheEntries(type, oid, modifyInfo);
		}
	}

	private <T extends ObjectType> void invalidateCacheEntries(Class<T> type, String oid, Object additionalInfo) {
		LocalObjectCache localObjectCache = getLocalObjectCache();
		if (localObjectCache != null) {
			localObjectCache.remove(oid);
		}
		LocalVersionCache localVersionCache = getLocalVersionCache();
		if (localVersionCache != null) {
			localVersionCache.remove(oid);
		}
		LocalQueryCache localQueryCache = getLocalQueryCache();
		if (localQueryCache != null) {
			clearQueryResultsLocally(localQueryCache, type, oid, additionalInfo, matchingRuleRegistry);
		}

		globalObjectCache.remove(oid);
		clearQueryResultsGlobally(type, oid, additionalInfo, matchingRuleRegistry);
		cacheDispatcher.dispatch(type, oid);
	}

	public <T extends ObjectType> void clearQueryResultsLocally(LocalQueryCache cache, Class<T> type, String oid,
			Object additionalInfo,
			MatchingRuleRegistry matchingRuleRegistry) {
		// TODO implement more efficiently

		ChangeDescription change = ChangeDescription.getFrom(type, oid, additionalInfo);

		long start = System.currentTimeMillis();
		int all = 0;
		int removed = 0;
		Iterator<Map.Entry<QueryKey, SearchResultList>> iterator = cache.getEntryIterator();
		while (iterator.hasNext()) {
			Map.Entry<QueryKey, SearchResultList> entry = iterator.next();
			QueryKey queryKey = entry.getKey();
			all++;
			if (change.mayAffect(queryKey, entry.getValue(), matchingRuleRegistry)) {
				LOGGER.trace("Removing (from local cache) query for type={}, change={}: {}", type, change, queryKey.getQuery());
				iterator.remove();
				removed++;
			}
		}
		LOGGER.trace("Removed (from local cache) {} (of {}) query result entries of type {} in {} ms", removed, all, type, System.currentTimeMillis() - start);
	}

	public <T extends ObjectType> void clearQueryResultsGlobally(Class<T> type, String oid, Object additionalInfo,
			MatchingRuleRegistry matchingRuleRegistry) {
		// TODO implement more efficiently

		ChangeDescription change = ChangeDescription.getFrom(type, oid, additionalInfo);

		long start = System.currentTimeMillis();
		AtomicInteger all = new AtomicInteger(0);
		AtomicInteger removed = new AtomicInteger(0);

		globalQueryCache.invokeAll(entry -> {
			QueryKey queryKey = entry.getKey();
			all.incrementAndGet();
			if (change.mayAffect(queryKey, entry.getValue(), matchingRuleRegistry)) {
				LOGGER.trace("Removing (from global cache) query for type={}, change={}: {}", type, change, queryKey.getQuery());
				entry.remove();
				removed.incrementAndGet();
			}
			return null;
		});
		LOGGER.trace("Removed (from global cache) {} (of {}) query result entries of type {} in {} ms", removed, all, type, System.currentTimeMillis() - start);
	}

	@NotNull
	@Override
	public <T extends ObjectType> DeleteObjectResult deleteObject(Class<T> type, String oid, OperationResult parentResult)
			throws ObjectNotFoundException {
		Long startTime = repoOpStart();
		DeleteObjectResult deleteInfo = null;
		try {
			deleteInfo = repositoryService.deleteObject(type, oid, parentResult);
			return deleteInfo;
		} finally {
			repoOpEnd(startTime);
			invalidateCacheEntries(type, oid, deleteInfo);
		}
	}

	@Override
	public <F extends FocusType> PrismObject<F> searchShadowOwner(
			String shadowOid, Collection<SelectorOptions<GetOperationOptions>> options, OperationResult parentResult) {
		// TODO cache the search operation?
		PrismObject<F> ownerObject;
		Long startTime = repoOpStart();
		try {
			ownerObject = repositoryService.searchShadowOwner(shadowOid, options, parentResult);
		} finally {
			repoOpEnd(startTime);
		}
		if (ownerObject != null && getPassReason(options, FocusType.class) == null) {
			boolean readOnly = GetOperationOptions.isReadOnly(SelectorOptions.findRootOptions(options));
			LocalObjectCache localObjectCache = getLocalObjectCache();
			cacheLoadedObject(ownerObject, readOnly, localObjectCache);
		}
		return ownerObject;
	}

	private PassReason getPassReason(Collection<SelectorOptions<GetOperationOptions>> options, Class<?> objectType) {
		if (alwaysNotCacheable(objectType)) {
			return PassReason.NOT_CACHEABLE_TYPE;
		}
		if (options == null || options.isEmpty()) {
			return null;
		}
		if (options.size() > 1) {
			//LOGGER.info("Cache: PASS REASON: size>1: {}", options);
			return PassReason.MULTIPLE_OPTIONS;
		}
		SelectorOptions<GetOperationOptions> selectorOptions = options.iterator().next();
		if (!selectorOptions.isRoot()) {
			//LOGGER.info("Cache: PASS REASON: !root: {}", options);
			return PassReason.NON_ROOT_OPTIONS;
		}
		GetOperationOptions options1 = selectorOptions.getOptions();
		// TODO FIX THIS!!!
		if (options1 == null ||
				options1.equals(new GetOperationOptions()) ||
				options1.equals(GetOperationOptions.createAllowNotFound()) ||
				options1.equals(GetOperationOptions.createExecutionPhase()) ||
				options1.equals(GetOperationOptions.createReadOnly()) ||
				options1.equals(GetOperationOptions.createNoFetch())) {
			return null;
		}
		if (options1.equals(GetOperationOptions.createRetrieve(RetrieveOption.INCLUDE))) {
			if (SelectorOptions.isRetrievedFullyByDefault(objectType)) {
				return null;
			} else {
				//LOGGER.info("Cache: PASS REASON: INCLUDE for {}: {}", objectType, options);
				return PassReason.INCLUDE_OPTION_PRESENT;
			}
		}
		//LOGGER.info("Cache: PASS REASON: other: {}", options);
		return PassReason.UNSUPPORTED_OPTION;
	}

	@Override
	@Deprecated
	public PrismObject<UserType> listAccountShadowOwner(String accountOid, OperationResult parentResult)
			throws ObjectNotFoundException {
		Long startTime = repoOpStart();
		try {
			return repositoryService.listAccountShadowOwner(accountOid, parentResult);
		} finally {
			repoOpEnd(startTime);
		}
	}

	@Override
	public <T extends ShadowType> List<PrismObject<T>> listResourceObjectShadows(String resourceOid,
			Class<T> resourceObjectShadowType, OperationResult parentResult) throws ObjectNotFoundException,
            SchemaException {
		Long startTime = repoOpStart();
		try {
			return repositoryService.listResourceObjectShadows(resourceOid, resourceObjectShadowType, parentResult);
		} finally {
			repoOpEnd(startTime);
		}
	}

	@Override
	public <T extends ObjectType> String getVersion(Class<T> type, String oid, OperationResult parentResult)
			throws ObjectNotFoundException, SchemaException {
		CachePerformanceCollector collector = CachePerformanceCollector.INSTANCE;
		LocalVersionCache localCache = getLocalVersionCache();
		Context local = localCache != null ?
				new Context(localCache.getConfiguration(), type) :
				new Context(cacheConfigurationManager.getConfiguration(LOCAL_REPO_VERSION_CACHE), type);

		if (alwaysNotCacheable(type)) {
			if (localCache != null) {
				localCache.registerPass();
			}
			collector.registerPass(LocalVersionCache.class, type, local.statisticsLevel);
			log("Cache: PASS (local) getVersion {} ({})", local.tracePass, oid, type.getSimpleName());
			Long startTime = repoOpStart();
			try {
				return repositoryService.getVersion(type, oid, parentResult);
			} finally {
				repoOpEnd(startTime);
			}
		}
		if (localCache == null) {
			log("Cache: NULL {} ({})", false, oid, type.getSimpleName());
			registerNotAvailable(LocalVersionCache.class, type, local.statisticsLevel);
		} else {
			String version = local.supports ? localCache.get(oid) : null;
			if (version != null) {
				localCache.registerHit();
				collector.registerHit(LocalVersionCache.class, type, local.statisticsLevel);
				log("Cache: HIT (local) getVersion {} ({})", false, oid, type.getSimpleName());
				return version;
			}
			if (local.supports) {
				localCache.registerMiss();
				collector.registerMiss(LocalVersionCache.class, type, local.statisticsLevel);
				log("Cache: MISS (local) getVersion {} ({})", local.traceMiss, oid, type.getSimpleName());
			} else {
				localCache.registerPass();
				collector.registerPass(LocalVersionCache.class, type, local.statisticsLevel);
				log("Cache: PASS (local) (cfg) getVersion {} ({})", local.tracePass, oid, type.getSimpleName());
			}
		}
		String version;
		Long startTime = repoOpStart();
		try {
			version = repositoryService.getVersion(type, oid, parentResult);
		} finally {
			repoOpEnd(startTime);
		}
		cacheObjectVersion(localCache, local.supports, oid, version);
		return version;
	}

	@Override
	public RepositoryDiag getRepositoryDiag() {
		Long startTime = repoOpStart();
		try {
			return repositoryService.getRepositoryDiag();
		} finally {
			repoOpEnd(startTime);
		}
	}

	@Override
	public void repositorySelfTest(OperationResult parentResult) {
		Long startTime = repoOpStart();
		try {
			repositoryService.repositorySelfTest(parentResult);
		} finally {
			repoOpEnd(startTime);
		}
	}

    @Override
    public void testOrgClosureConsistency(boolean repairIfNecessary, OperationResult testResult) {
    	Long startTime = repoOpStart();
		try {
			repositoryService.testOrgClosureConsistency(repairIfNecessary, testResult);
		} finally {
			repoOpEnd(startTime);
		}
    }

    private <T extends ObjectType> void locallyCacheObject(LocalObjectCache cache, boolean supports, PrismObject<T> object, boolean readOnly) {
		if (cache != null && supports) {
			cache.put(object.getOid(), prepareObjectToCache(object, readOnly));
		}
	}

    private <T extends ObjectType> void locallyCacheObjectWithoutCloning(LocalObjectCache cache, boolean supports, PrismObject<T> object) {
		if (cache != null && supports) {
			cache.put(object.getOid(), object);
		}
	}

	private void cacheObjectVersion(LocalVersionCache cache, boolean supports, String oid, String version) {
		if (cache != null && supports) {
			cache.put(oid, version);
		}
	}

	@Override
	public boolean isAnySubordinate(String upperOrgOid, Collection<String> lowerObjectOids)
			throws SchemaException {
		Long startTime = repoOpStart();
		try {
			return repositoryService.isAnySubordinate(upperOrgOid, lowerObjectOids);
		} finally {
			repoOpEnd(startTime);
		}
	}

	@Override
	public <O extends ObjectType> boolean isDescendant(PrismObject<O> object, String orgOid)
			throws SchemaException {
		Long startTime = repoOpStart();
		try {
			return repositoryService.isDescendant(object, orgOid);
		} finally {
			repoOpEnd(startTime);
		}
	}

	@Override
	public <O extends ObjectType> boolean isAncestor(PrismObject<O> object, String oid)
			throws SchemaException {
		Long startTime = repoOpStart();
		try {
			return repositoryService.isAncestor(object, oid);
		} finally {
			repoOpEnd(startTime);
		}
	}

	@Override
	public <O extends ObjectType> boolean selectorMatches(ObjectSelectorType objectSelector,
			PrismObject<O> object, ObjectFilterExpressionEvaluator filterEvaluator, Trace logger, String logMessagePrefix) throws SchemaException, ObjectNotFoundException, ExpressionEvaluationException, CommunicationException, ConfigurationException, SecurityViolationException {
		Long startTime = repoOpStart();
		try {
			return repositoryService.selectorMatches(objectSelector, object, filterEvaluator, logger, logMessagePrefix);
		} finally {
			repoOpEnd(startTime);
		}
	}

	private void log(String message, boolean info, Object... params) {
		CacheUtil.log(LOGGER, PERFORMANCE_ADVISOR, message, info, params);
	}

	@Override
	public long advanceSequence(String oid, OperationResult parentResult) throws ObjectNotFoundException,
			SchemaException {
		Long startTime = repoOpStart();
		try {
			return repositoryService.advanceSequence(oid, parentResult);
		} finally {
			repoOpEnd(startTime);
			invalidateCacheEntries(SequenceType.class, oid, null);
		}
	}

	@Override
	public void returnUnusedValuesToSequence(String oid, Collection<Long> unusedValues, OperationResult parentResult)
			throws ObjectNotFoundException, SchemaException {
		Long startTime = repoOpStart();
		try {
			repositoryService.returnUnusedValuesToSequence(oid, unusedValues, parentResult);
		} finally {
			repoOpEnd(startTime);
			invalidateCacheEntries(SequenceType.class, oid, null);
		}
	}

	@Override
	public RepositoryQueryDiagResponse executeQueryDiagnostics(RepositoryQueryDiagRequest request, OperationResult result) {
		Long startTime = repoOpStart();
		try {
			return repositoryService.executeQueryDiagnostics(request, result);
		} finally {
			repoOpEnd(startTime);
		}
	}

	@Override
	public QName getApproximateSupportedMatchingRule(Class<?> dataType, QName originalMatchingRule) {
		Long startTime = repoOpStart();
		try {
			return repositoryService.getApproximateSupportedMatchingRule(dataType, originalMatchingRule);
		} finally {
			repoOpEnd(startTime);
		}
	}

	@Override
	public void applyFullTextSearchConfiguration(FullTextSearchConfigurationType fullTextSearch) {
		Long startTime = repoOpStart();
		try {
			repositoryService.applyFullTextSearchConfiguration(fullTextSearch);
		} finally {
			repoOpEnd(startTime);
		}
	}

	@Override
	public FullTextSearchConfigurationType getFullTextSearchConfiguration() {
		Long startTime = repoOpStart();
		try {
			return repositoryService.getFullTextSearchConfiguration();
		} finally {
			repoOpEnd(startTime);
		}
	}

	@Override
	public void postInit(OperationResult result) throws SchemaException {
		repositoryService.postInit(result);     // TODO resolve somehow multiple calls to repositoryService postInit method
		globalObjectCache.initialize();
		globalQueryCache.initialize();
	}

	@Override
	public ConflictWatcher createAndRegisterConflictWatcher(String oid) {
		return repositoryService.createAndRegisterConflictWatcher(oid);
	}

	@Override
	public void unregisterConflictWatcher(ConflictWatcher watcher) {
		repositoryService.unregisterConflictWatcher(watcher);
	}

	@Override
	public boolean hasConflict(ConflictWatcher watcher, OperationResult result) {
		return repositoryService.hasConflict(watcher, result);
	}

	private boolean hasVersionChanged(Class<? extends ObjectType> objectType, String oid, GlobalCacheObjectValue object, OperationResult result)
			throws ObjectNotFoundException, SchemaException {

		try {
			String version = repositoryService.getVersion(objectType, oid, result);
			return !Objects.equals(version, object.getObjectVersion());
		} catch (ObjectNotFoundException | SchemaException ex) {
			globalObjectCache.remove(oid);
			throw ex;
		}
	}

	private boolean shouldCheckVersion(GlobalCacheObjectValue object) {
		return object.getTimeToLive() < System.currentTimeMillis();
	}

	private <T extends ObjectType> PrismObject<T> loadAndCacheObject(Class<T> objectClass, String oid,
			Collection<SelectorOptions<GetOperationOptions>> options, boolean readOnly, LocalObjectCache localCache,
			boolean localCacheSupports, OperationResult result)
			throws ObjectNotFoundException, SchemaException {
		try {
			PrismObject<T> object = getObjectInternal(objectClass, oid, options, result);
			PrismObject<T> objectToCache = prepareObjectToCache(object, readOnly);
			globallyCacheObjectWithoutCloning(objectToCache);
			locallyCacheObjectWithoutCloning(localCache, localCacheSupports, objectToCache);
			return object;
		} catch (ObjectNotFoundException | SchemaException ex) {
			globalObjectCache.remove(oid);
			throw ex;
		}
	}

	private <T extends ObjectType> void globallyCacheObjectWithoutCloning(PrismObject<T> objectToCache) {
		CacheConfiguration cacheConfiguration = globalObjectCache.getConfiguration();
		Class<? extends ObjectType> type = objectToCache.asObjectable().getClass();
		CacheObjectTypeConfiguration typeConfiguration = globalObjectCache.getConfiguration(type);
		if (cacheConfiguration != null && cacheConfiguration.supportsObjectType(type)) {
			long ttl = System.currentTimeMillis() + getTimeToVersionCheck(typeConfiguration, cacheConfiguration);
			globalObjectCache.put(new GlobalCacheObjectValue<>(objectToCache, ttl));
		}
	}

	private <T extends ObjectType> SearchResultList<PrismObject<T>> executeAndCacheSearch(QueryKey key,
			Collection<SelectorOptions<GetOperationOptions>> options, boolean readOnly, LocalQueryCache localCache,
			boolean localCacheSupports, OperationResult result)
			throws SchemaException {
		try {
			//noinspection unchecked
			SearchResultList<PrismObject<T>> searchResult = (SearchResultList) searchObjectsInternal(key.getType(), key.getQuery(), options, result);
			locallyCacheSearchResult(localCache, localCacheSupports, key, readOnly, searchResult);
			globallyCacheSearchResult(key, readOnly, searchResult);
			return searchResult;
		} catch (SchemaException ex) {
			globalQueryCache.remove(key);
			throw ex;
		}
	}

	@NotNull
	private <T extends ObjectType> PrismObject<T> prepareObjectToCache(PrismObject<T> object, boolean readOnly) {
		PrismObject<T> objectToCache;
		if (readOnly) {
			object.setImmutable(true);
			objectToCache = object;
		} else {
			// We are going to return the object (as mutable), so we must store a clone
			objectToCache = object.clone();
		}
		return objectToCache;
	}

	@Override
	public <T extends ObjectType> void addDiagnosticInformation(Class<T> type, String oid, DiagnosticInformationType information,
			OperationResult parentResult) throws ObjectNotFoundException, SchemaException, ObjectAlreadyExistsException {
		delay(modifyRandomDelayRange);
		Long startTime = repoOpStart();
		try {
			repositoryService.addDiagnosticInformation(type, oid, information, parentResult);
		} finally {
			repoOpEnd(startTime);
			// this changes the object. We are too lazy to apply changes ourselves, so just invalidate
			// the object in cache
			// TODO specify additional info more precisely (but currently we use this method only in connection with TaskType
			//  and this kind of object is not cached anyway, so let's ignore this
			invalidateCacheEntries(type, oid, null);
		}
	}

	@Override
	public PerformanceMonitor getPerformanceMonitor() {
		return repositoryService.getPerformanceMonitor();
	}

	private static class Context {
		CacheConfiguration cacheConfig;
		CacheObjectTypeConfiguration typeConfig;
		boolean supports;
		CacheConfiguration.StatisticsLevel statisticsLevel;
		boolean traceMiss;
		boolean tracePass;

		Context(CacheConfiguration configuration, Class<?> type) {
			if (configuration != null) {
				cacheConfig = configuration;
				typeConfig = configuration.getForObjectType(type);
				supports = configuration.supportsObjectType(type);
				statisticsLevel = CacheConfiguration.getStatisticsLevel(typeConfig, cacheConfig);
				traceMiss = CacheConfiguration.getTraceMiss(typeConfig, cacheConfig);
				tracePass = CacheConfiguration.getTracePass(typeConfig, cacheConfig);
			}
		}
	}

	enum PassReason {
		NOT_CACHEABLE_TYPE, MULTIPLE_OPTIONS, NON_ROOT_OPTIONS, UNSUPPORTED_OPTION, INCLUDE_OPTION_PRESENT
	}
}
