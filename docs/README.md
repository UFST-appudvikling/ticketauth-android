# TicketAuth Android

Small android library that simplifies OAuth Code flow (web logins).

# Usage

## Setup

First setup the library. Call this from your activity's onCreate method (fx):
```
TicketAuth.setup(
    TicketAuthConfig.Builder()
        .sharedPrefs(getSharedPreferences("appsettings", Context.MODE_PRIVATE))
        .context(this)
        .dcsBaseUrl("https://baseurl")
        .clientId("clientId")
        .scopes("scope1 scope2 scope3")
        .debug(true)
        .build()
)

TicketAuth.installActivityProvider { this }
```

Auth state is automatically persisted (which is why setup takes a SharedPreferences object).

## Authenticator object
When TicketAuth is configured you can get a Authenticator object like this:

```
val authenticator = TicketAuth.authenticator()
```
This is used for communicating with the library.

### Prepare network code
For TicketAuth to do its thing, you need to call a method (prepareCall) before issuing
your network calls. If it returns false it means that the library failed in obtaining
a valid access token. 

```
if(authenticator.prepareCall()) {
    // perform network call
} else {
    // login failed or was cancelled by the user
    // deal with this at the application level
}
```

Authenticator.prepareCall() works by blocking the caller until token refresh or login can take place.

### Manual login

TicketAuth automatically attempts to login if needed when calling Authenticator.prepareCall()
but if you want to login without calling an endpoint you can use:

```
if(!TicketAuth.isAuthorized) {
    TicketAuth.authenticator().login()
}
```

__You can call this AND prepareCall at the same, 
library will only ever show one login or do one token refresh.__

### Logout
If you want to run the oauth logout flow call:
```
authenticator.logout()
```

### Clear token manually
You might need to clear the current auth state (and all tokens) manually on the response
to some event. To do this call:

```
authenticator.clearToken()
```

