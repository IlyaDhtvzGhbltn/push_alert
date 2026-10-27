using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Security.Cryptography;
using System.Security.Cryptography.X509Certificates;
using System.Text.Json;
using Microsoft.Extensions.Options;
using MQTTnet;
using MQTTnet.Protocol;
using MQTTnet.Server;

namespace AlertBackend;

public class MqttSettings
{
    public int Port { get; set; } = 8883;
    public string Username { get; set; } = "";
    public string Password { get; set; } = "";
    public string Topic { get; set; } = "infra/alerts";
    public string CertificatePath { get; set; } = "certs/broker.pfx";
    public string CertificatePassword { get; set; } = "";
}

public class MqttBroker(IOptions<MqttSettings> options, ILogger<MqttBroker> logger) : IHostedService
{
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);

    private readonly MqttSettings _settings = options.Value;
    private MqttServer? _server;

    public async Task StartAsync(CancellationToken cancellationToken)
    {
        var certificate = LoadOrCreateCertificate();

        var serverOptions = new MqttServerOptionsBuilder()
            .WithoutDefaultEndpoint()
            .WithEncryptedEndpoint()
            .WithEncryptedEndpointPort(_settings.Port)
            .WithEncryptionCertificate(certificate)
            .WithPersistentSessions()
            .Build();

        _server = new MqttServerFactory().CreateMqttServer(serverOptions);

        _server.ValidatingConnectionAsync += e =>
        {
            if (e.UserName != _settings.Username || e.Password != _settings.Password)
            {
                e.ReasonCode = MqttConnectReasonCode.BadUserNameOrPassword;
            }
            return Task.CompletedTask;
        };
        _server.ClientConnectedAsync += e =>
        {
            logger.LogInformation("Client connected: {ClientId}", e.ClientId);
            return Task.CompletedTask;
        };
        _server.ClientDisconnectedAsync += e =>
        {
            logger.LogInformation("Client disconnected: {ClientId}", e.ClientId);
            return Task.CompletedTask;
        };

        await _server.StartAsync();
        logger.LogInformation("MQTTS broker listening on port {Port}, topic {Topic}", _settings.Port, _settings.Topic);
    }

    public async Task StopAsync(CancellationToken cancellationToken)
    {
        if (_server != null) await _server.StopAsync();
    }

    public async Task PublishAsync(AlertDto alert)
    {
        if (_server == null) throw new InvalidOperationException("Broker is not started");

        var message = new MqttApplicationMessageBuilder()
            .WithTopic(_settings.Topic)
            .WithPayload(JsonSerializer.SerializeToUtf8Bytes(alert, Json))
            .WithQualityOfServiceLevel(MqttQualityOfServiceLevel.AtLeastOnce)
            .Build();

        await _server.InjectApplicationMessage(new InjectedMqttApplicationMessage(message) { SenderClientId = "backend" });
        logger.LogInformation("Published {Severity} {Id}", alert.Severity, alert.Id);
    }

    private X509Certificate2 LoadOrCreateCertificate()
    {
        var pfxPath = Path.GetFullPath(_settings.CertificatePath);
        if (!File.Exists(pfxPath))
        {
            Directory.CreateDirectory(Path.GetDirectoryName(pfxPath)!);
            using var created = CreateSelfSigned();
            File.WriteAllBytes(pfxPath, created.Export(X509ContentType.Pfx, _settings.CertificatePassword));
            File.WriteAllText(Path.ChangeExtension(pfxPath, ".crt"), created.ExportCertificatePem());
            logger.LogWarning("Created self-signed certificate {Path}. Copy {Crt} into the Android app (res/raw/broker.crt)",
                pfxPath, Path.ChangeExtension(pfxPath, ".crt"));
        }
        return X509CertificateLoader.LoadPkcs12FromFile(pfxPath, _settings.CertificatePassword);
    }

    private static X509Certificate2 CreateSelfSigned()
    {
        using var key = ECDsa.Create(ECCurve.NamedCurves.nistP256);
        var request = new CertificateRequest("CN=AlertBackend MQTT", key, HashAlgorithmName.SHA256);

        var san = new SubjectAlternativeNameBuilder();
        san.AddDnsName("localhost");
        san.AddIpAddress(IPAddress.Loopback);
        san.AddIpAddress(IPAddress.Parse("10.0.2.2"));
        foreach (var ip in LocalIPv4Addresses()) san.AddIpAddress(ip);
        request.CertificateExtensions.Add(san.Build());
        request.CertificateExtensions.Add(new X509BasicConstraintsExtension(false, false, 0, false));
        request.CertificateExtensions.Add(new X509EnhancedKeyUsageExtension(
            [new Oid("1.3.6.1.5.5.7.3.1")], false));

        return request.CreateSelfSigned(DateTimeOffset.UtcNow.AddDays(-1), DateTimeOffset.UtcNow.AddYears(5));
    }

    private static IEnumerable<IPAddress> LocalIPv4Addresses() =>
        NetworkInterface.GetAllNetworkInterfaces()
            .Where(n => n.OperationalStatus == OperationalStatus.Up)
            .SelectMany(n => n.GetIPProperties().UnicastAddresses)
            .Select(a => a.Address)
            .Where(a => a.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(a));
}
