function extractResult(node, tagName, callback) {
    var n = node.getElementsByTagName(tagName);
    if (n.length > 0) return callback(n[0].textContent);
}
function addRow(tableBody, text) {
    var tr = document.createElement('TR');
    var td = document.createElement('TD');
    td.innerHTML = text;
    tr.appendChild(td);
    tableBody.appendChild(tr);
}
function showProgress() {
    $("#uploads_process").hide();
    $("#uploads_progress").show();
    var p = $("#uploads_progress");
    p.text(p.text() + ".");
    p.show();
}
function showResults() {

    ajaxAsync("check-upload-status", "", function(response) {
        var results = response.documentElement.getElementsByTagName("result");
        var complete = response.documentElement.getElementsByTagName("complete");
        var tableBody = document.getElementById("uploaded");
        for (var i = 0; i < results.length; i++) {

            var res = extractResult(results[i], "done", function(text) {
                // TODO: get Strava user friendly name, or include a time?
                return "Done <a href=https://www.strava.com/activities/" + text + ">" + text + "</a>";
            }) || extractResult(results[i], "duplicate", function(text) {
                if (text ==0) {
                    return "Duplicate";
                } else {
                    return "Duplicate of <a href=https://www.strava.com/activities/" + text + ">" + text + "</a>";
                }
            }) || extractResult(results[i], "error", function(text) {
                return "Error " + text;
            });
            if (res) addRow(tableBody, res);
        }
        if (complete.length == 0) {
            showProgress();
            setTimeout(showResults, 1000);
        } else {
            $("#uploads_complete").show();
            $("#uploads_process").hide();
            $("#uploads_progress").hide();
        }
    }, function (failure) {
        console.log(failure);
        setTimeout(showResults, 1000);
    });
}
