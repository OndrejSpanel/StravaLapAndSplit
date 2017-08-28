/** @return {string} */
function getLocale() {
    return navigator.languages[0] || navigator.language;
}
/**
 * @param {string} t
 * @return {string}
 */
function formatDateTime(t) {
    var locale = getLocale();
    var date = new Date(t);
    return new Intl.DateTimeFormat(
        locale,
        {
            year: "numeric",
            month: "numeric",
            day: "numeric",
            hour: "numeric",
            minute: "numeric",
        }
    ).format(date)
}
/**
 * @param {string} t
 * @return {string}
 */
function formatTime(t) {
    var locale = getLocale();
    var date = new Date(t);
    return new Intl.DateTimeFormat(
        locale,
        {
            //year: "numeric",
            //month: "numeric",
            //day: "numeric",
            hour: "numeric",
            minute: "numeric",
            //timeZoneName: "short"
        }
    ).format(date)
}
function formatTimeSec(t) {
    var locale = getLocale();
    var date = new Date(t);
    return new Intl.DateTimeFormat(
        locale,
        {
            //year: "numeric",
            //month: "numeric",
            //day: "numeric",
            hour: "numeric",
            minute: "numeric",
            second: "numeric"
        }
    ).format(date)
}
