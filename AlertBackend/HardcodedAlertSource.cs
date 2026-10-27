namespace AlertBackend;

public record AlertDto(string Id, string Severity, string Title, string? Body, string? Host, long Ts);

public interface IAlertSource
{
    AlertDto Next();
}

public class HardcodedAlertSource : IAlertSource
{
    private static readonly (string Id, string Severity, string Title, string Body, string Host)[] Alerts =
    [
        ("pg-01-down", "critical", "DB down", "pg-01 не отвечает", "pg-01"),
        ("fs-03-disk", "warning", "Диск заполнен на 90%", "/var: 90%", "fs-03"),
        ("pg-01-down", "resolved", "DB up", "pg-01 снова доступен", "pg-01"),
        ("web-02-down", "critical", "Сайт недоступен", "HTTP 502 на /health", "web-02"),
        ("web-02-down", "resolved", "Сайт доступен", "/health отвечает 200", "web-02"),
    ];

    private int _index = -1;

    public AlertDto Next()
    {
        var i = Interlocked.Increment(ref _index) % Alerts.Length;
        var a = Alerts[i];
        return new AlertDto(a.Id, a.Severity, a.Title, a.Body, a.Host, DateTimeOffset.UtcNow.ToUnixTimeSeconds());
    }
}
