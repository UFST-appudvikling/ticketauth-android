# ticketauth-android

Android library that simplifies OAuth Code flow (web logins).

# Usage

First setup the library. Call this from your activity's onCreate method (fx):
```
TicketAuth.setup(
    TicketAuthConfig.Builder()
        .sharedPrefs(getSharedPreferences("appsettings", Context.MODE_PRIVATE))
        .context(this)
        .dcsBaseUrl("https://baseurl")
        .clientId("clientId")
        .scopes("openid")
        .debug(true)
        .build()
)

TicketAuth.installActivityProvider { this }
```

