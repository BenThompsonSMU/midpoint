/*
 * Copyright (c) 2010-2020 Evolveum and contributors
 *
 * This work is dual-licensed under the Apache License 2.0
 * and European Union Public License. See LICENSE file for details.
 */

// current ace editor version v1.2.9

var ACE_EDITOR_POSTFIX = "_editor";
var DISABLED_CLASS = "disabled";
$.aceEditors = {};

function initEditor(textAreaId, readonly, resize, height, minHeight, mode) {
    var jqTextArea = '#' + textAreaId;
    var editorId = textAreaId + ACE_EDITOR_POSTFIX;
    var jqEditor = '#' + editorId;

    var newHeight = height;
    if (resize) {
        newHeight = getMaxSizeHeight(minHeight);
    }
    $('<div id="' + editorId + '" class="aceEditor" style="height: ' + newHeight + 'px;"></div>').insertAfter($('#' + textAreaId));

    $(jqEditor).text($(jqTextArea).val());
    $(jqTextArea).hide();

    var langTools = ace.require("ace/ext/language_tools");
    //todo implement completer based
    // var completer = {
    //
    //     getCompletions: function(editor, session, pos, prefix, callback) {
    //         //example
    //         var completions = [];
    //         completions.push({ name:"testing1", value:"testing1", meta: "code1" });
    //         completions.push({ name:"testing2", value:"testing2", meta: "code2" });
    //         callback(null, completions);
    //     }
    // }
    // langTools.addCompleter(completer);

    var editor = ace.edit(editorId);

    editor.setOptions({
        enableBasicAutocompletion: true
    });

    editor.getSession().setTabSize(3);

    editor.setTheme("ace/theme/eclipse");
    if (mode != null) {
        editor.getSession().setMode(mode);
    }
    editor.setShowPrintMargin(false);
    editor.setFadeFoldWidgets(false);
    setReadonly(jqEditor, editor, readonly);
    editor.on('blur', function () {
        $(jqTextArea).val(editor.getSession().getValue());
        $(jqTextArea).trigger('blur');
    });
    editor.on('change', function () {
        $(jqTextArea).val(editor.getSession().getValue());
        $(jqTextArea).trigger('change');
    });

    //add editor to global map, so we can find it later
    if ($.aceEditors === undefined) {
        $.aceEditors = {}
    }
    $.aceEditors[editorId] = editor;

    //todo handle readonly for text area [lazyman] add "disabled" class to .ace_scroller

    // $(document).ready(function () {
    //     if (height < minHeight) {
    //         height = minHeight;
    //     }
    //
    //     if (resize) {
    //         resizeToMaxHeight(editorId, minHeight);
    //     } else {
    //         resizeToFixedHeight(editorId, height);
    //     }
    // });
}

function resizeToMaxHeight(editorId, minHeight) {
    //38 + 1 + 21 is menu outer height
    var newHeight = getMaxSizeHeight(minHeight);

    resizeToFixedHeight(editorId, newHeight);
}

function getMaxSizeHeight(minHeight) {
    var footerHeight = $('footer.main-footer').outerHeight(true);

    var newHeight;
    if (footerHeight) {
        newHeight = $(document).innerHeight()
                - footerHeight - $('header.main-header').outerHeight(true);
    } else {
    newHeight = $(document).innerHeight() - $('header.main-header').outerHeight(true);
    }

    var boxHeader = $('div.box-header').outerHeight(true);
    var buttonsBar = $('div.main-button-bar').outerHeight(true);
    if (buttonsBar){
        newHeight = newHeight - buttonsBar;
    }
    if (boxHeader){
        newHeight = newHeight - boxHeader;
    }
    if (newHeight < minHeight) {
        newHeight = minHeight;
    }
    return newHeight;
}

function resizeToFixedHeight(editorId, height) {
    $('#' + editorId).height(height.toString() + "px");
    $('#' + editorId + '-section').height(height.toString() + "px");

    $.aceEditors[editorId].resize();
}

function refreshReadonly(textAreaId, readonly) {
    var jqTextArea = '#' + textAreaId;

    var editorId = textAreaId + ACE_EDITOR_POSTFIX;
    var jqEditor = '#' + editorId;

    var editor = $.aceEditors[editorId];
    setReadonly(jqEditor, editor, readonly);
    editor.focus();
}

function setReadonly(jqEditor, editor, readonly) {
    editor.setReadOnly(readonly);
    if (readonly) {
        $(jqEditor).addClass(DISABLED_CLASS);
    } else {
        $(jqEditor).removeClass(DISABLED_CLASS);
    }
}

function reloadTextarea(editor) {
    editor.setReadOnly(readonly);
    if (readonly) {
        $(jqEditor).addClass(DISABLED_CLASS);
    } else {
        $(jqEditor).removeClass(DISABLED_CLASS);
    }
}