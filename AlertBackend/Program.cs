using AlertBackend;
using Microsoft.Extensions.Options;

var builder = WebApplication.CreateBuilder(args);

builder.Services.Configure<MqttSettings>(builder.Configuration.GetSection("Mqtt"));
builder.Services.Configure<WebhookSettings>(builder.Configuration.GetSection("Webhooks"));
builder.Services.AddSingleton<MqttBroker>();
builder.Services.AddHostedService(sp => sp.GetRequiredService<MqttBroker>());
builder.Services.AddSingleton<IAlertSource, HardcodedAlertSource>();

var app = builder.Build();

app.MapPost("/api/alerts/test", async (IAlertSource source, MqttBroker broker) =>
{
    var alert = source.Next();
    await broker.PublishAsync(alert);
    return alert;
});

app.MapPost("/api/webhooks/zabbix", async (HttpRequest request, ZabbixEvent body, IOptions<WebhookSettings> settings, MqttBroker broker) =>
{
    if (!ZabbixWebhook.IsAuthorized(request, settings.Value.ZabbixToken)) return Results.Unauthorized();

    var alert = ZabbixWebhook.ToAlert(body);
    if (alert == null) return Results.BadRequest("eventId and name are required");

    await broker.PublishAsync(alert);
    return Results.Ok(alert);
});

app.Run();
