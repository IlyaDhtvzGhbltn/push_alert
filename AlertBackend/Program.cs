using AlertBackend;

var builder = WebApplication.CreateBuilder(args);

builder.Services.Configure<MqttSettings>(builder.Configuration.GetSection("Mqtt"));
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

app.Run();
