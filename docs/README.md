# TicketAuth Android

Small android library that simplifies OAuth Code flow (web logins).

# Usage

## Gradle dependency
```
implementation("com.github.skat:ticketauth-android:$VERSION")
```
Where $VERSION is a release, tag or branch snap. Check [jitpack.io](https://jitpack.io)
for all available options.

## Setup
You can call TicketAuth.setup() from anywhere. The library will reconfigure itself to the desired
configuration.

### Host Application Activity Setup
However the host app must inform TicketAuth which Activity to use for launching
browser tabs and utility activities. Call TicketAuth.setHostActivity in the OnCreate method
of your app's MainActivity (or whatever activity you wish to launch TicketAuth related intents from).
Upon calling TicketAuth.setHostActivity the library will update activity launchers, but it is the
apps responsebility to make sure it gets called after configuration changes etc (that recreate the host apps activity).

In your apps MainActivity onCreate method add this:
```
    TicketAuth.setHostActivity(this)
```

TicketAuth.setHostActivity can be called before or after TicketAuth.setup() but if the library
doesn't have a valid activity context when needed, an exception will be thrown.

### Auth Code
Setup the library for Auth Code which is OAuth/OpenConnect Code Authorization flow:
```
TicketAuth.setup(
    AuthCodeConfig.Builder()
        .dcsBaseUrl("https://baseurl")
        .clientId("clientId")
        .scopes("scope1 scope2 scope3")
        .debug(true)
        .build()
)
```
Auth state is automatically persisted between application restarts.

#### redirectUri
The library generates a default redirectUri which is packagename + ".ticketauth" if you for one
reason or another needs to manually specify a redirectUri, call this function:

```
    .redirectUri("myapp://callback")
```

If you specify a custom redirectUri you need to define a manifest override as well in your app:

```
<activity
        android:name="net.openid.appauth.RedirectUriReceiverActivity"
        android:exported="true"
        tools:node="replace"
        tools:ignore="MissingClass">
        <intent-filter>
            <action android:name="android.intent.action.VIEW"/>
            <category android:name="android.intent.category.DEFAULT"/>
            <category android:name="android.intent.category.BROWSABLE"/>
            <data android:scheme="myapp://"/>
        </intent-filter>
</activity>
```

__If you't don specify a custom redirectUri you don't have to add anything to your apps manifest
file.__

### Automated Auth
Setup the library for Automated login in which the library reads and present users from
a json configuration file. This file contains the necessary data to request an access token
from a third party endpoint. This feature is meant to ease testing.

___Automated login doesn't support token refresh and will default to relogin whenever the token expires.___

```
TicketAuth.setup(
    AutomatedAuthConfig.Builder()
        .userConfig(AutomatedAuthConfig.fromAssets("users.json"))
        .build()
)
```
User config is supplied in the form of a String containing json. You can use the helper function
fromAssets() (demonstrated above) for easy initialization in case you deploy the file with app.

The json configuration file is parsed and will throw an exception upon syntax errors as well as missing
fields etc.

Documentation for the configuration file is to be found elsewhere.

### Optional parameters
These parameters applies to all Auth Engines and can be added to TicketAuth.setup() via
the individual Auth Engines config builders (see above).

#### onNewAccessToken
If you need to get a callback whenever the library obtains a new access token, either through
token refresh or relogin, call this function:

```
    .onNewAccessToken { token ->
        // do something with token
    }
```

#### onAuthResult
Use this if you need a callback each time an AuthResult is obtained.
This is handy if you want to centralize error handling etc

```
   .onAuthResult { result ->
        // do something with result
    }
```

## Authenticator object
When TicketAuth is configured you can get a Authenticator object like this:

```
val authenticator = TicketAuth.authenticator()
```
This is used for communicating with the library.

### Prepare network code
For TicketAuth to do its thing, you need to call a method (prepareCall) before issuing
your network calls. If it returns AuthResult.ERROR it means that the library failed in obtaining
a valid access token. 

#### Return values

Following values can be returned by prepareCall:

- AuthResult.SUCCESS, we got a token, all good
- AuthResult.CANCELLED_FLOW, user cancelled the login flow (by closing the browser window)
- AuthResult.ERROR, Something went wrong, likely network error.

```
when(authenticator.prepareCall()) {
    AuthResult.CANCELLED_FLOW -> TODO() // return status so domain layer can decide what to do
    AuthResult.ERROR -> TODO() // // return status so domain layer can decide what to do
    AuthResult.SUCCESS -> TODO() // perform network call and return data or error
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

#### Optional callback
It is possible to pass a callback function to login inorder to notified whenever the login is
complete. This is useful for detecting if the user cancelled the webflow (by closing the browser)
etc.

```
TicketAuth.authenticator().login { result ->
    when(result) {
        AuthResult.SUCCESS -> {
            // do something
        }
        AuthResult.CANCELLED_FLOW -> {
            // do something
        }
        AuthResult.ERROR -> {
            // do something
        }
    }
}
```

__You can call this AND prepareCall at the same time, 
library will only ever show one login or do one token refresh.__

### Logout
If you want to run the oauth logout flow call:
```
authenticator.logout()
```

___When using Automated Auth no actual login endpoint is called. Instead local auth state is invalidated.___ 

#### Get notified when logout flow is complete
It is possible to get called back whenever the logout flow is completed.

```
TicketAuth.authenticator().logout { result ->
    when(result) {
        AuthResult.SUCCESS -> {
            // do something
        }
        AuthResult.CANCELLED_FLOW -> {
            // do something
        }
        AuthResult.ERROR -> {
            // do something
        }
    }
}
```

### Clear token manually
You might need to clear the current auth state (and all tokens) manually on the response
to some event. To do this call:

```
authenticator.clearToken()
```

