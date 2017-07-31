/**
 * @returns {XMLHttpRequest}
 */
function /** XMLHttpRequest */ ajax() {
    var xmlhttp;
    if (window.XMLHttpRequest) { // code for IE7+, Firefox, Chrome, Opera, Safari
        xmlhttp = new XMLHttpRequest();
    } else { // code  for IE6, IE5
        xmlhttp = new ActiveXObject("Microsoft.XMLHTTP");
    }
    return xmlhttp;
}

/**
 * @param {XMLHttpRequest} xmlhttp
 * @param {string} request
 * @param {string} [data]
 * @param {boolean} async
 */
function ajaxPost(xmlhttp, request, data, async) {
    xmlhttp.open("POST", request, async); // POST to prevent caching
    xmlhttp.setRequestHeader("Content-type", "text/plain");
    xmlhttp.send(data ? data: "");
}

/**
 * @param {XMLHttpRequest} xmlhttp
 * @param {string} request
 * @param {string} [data]
 * @param {boolean} async
 */
function ajaxPostRaw(xmlhttp, request, data, async) {
    xmlhttp.open("POST", request, async); // POST to prevent caching
    xmlhttp.responseType = "arraybuffer";
    xmlhttp.setRequestHeader("Content-type", "application/octet-stream");
    xmlhttp.send(data ? data: "");
}

function ajaxAsyncRaw(uri, data, callback, failure) {
    var xmlhttp = ajax();
    // the callback function to be callled when AJAX request comes back
    xmlhttp.onreadystatechange = function () {
        if (xmlhttp.readyState === 4) {
            if (xmlhttp.status >= 200 && xmlhttp.status < 300) {
                callback(xmlhttp.response, xmlhttp.status);
            } else if (failure) {
                failure(xmlhttp.response, xmlhttp.status);
            }
        }
    };
    ajaxPostRaw(xmlhttp, uri, data, true); // POST to prevent caching
}

function ajaxAsync(uri, data, callback, failure) {
    var xmlhttp = ajax();
    // the callback function to be callled when AJAX request comes back
    xmlhttp.onreadystatechange = function () {
        if (xmlhttp.readyState === 4) {
            if (xmlhttp.status >= 200 && xmlhttp.status < 300) {
                callback(xmlhttp.responseXML, xmlhttp.status);
            } else if (failure) {
                failure(xmlhttp.responseXML, xmlhttp.status);
            }
        }
    };
    ajaxPost(xmlhttp, uri, data, true); // POST to prevent caching
}

function reload() {
    location.reload()
}
function ajaxAction(uri) {
    // do action, reload page once done
    ajaxAsync(uri,"", reload);
}