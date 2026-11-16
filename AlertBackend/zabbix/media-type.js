var p = JSON.parse(value);

if (p.EventUpdateStatus === '1') {
    return 'skipped';
}

var request = new HttpRequest();
request.addHeader('Content-Type: application/json');
request.addHeader('Authorization: Bearer ' + p.Token);

var response = request.post(p.URL, JSON.stringify({
    eventId: p.EventId,
    eventValue: p.EventValue,
    severity: p.Severity,
    name: p.Name,
    host: p.Host,
    message: p.Message
}));

if (request.getStatus() !== 200) {
    throw 'HTTP ' + request.getStatus() + ': ' + response;
}

return 'OK';
