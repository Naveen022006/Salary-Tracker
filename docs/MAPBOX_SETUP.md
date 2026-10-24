# Mapbox key for the Spending map

The Spending map (Report → Spending map) uses Mapbox for the map pictures. The app needs one **public** token
(it starts with `pk.`). Without it the app works normally and the map says "Map is not set up".

## 1. Get the token (free)

1. Open https://account.mapbox.com/auth/signup and make a free account (no card is needed for the free tier).
   If you already have an account, sign in at https://account.mapbox.com.
2. Open **Tokens**: https://account.mapbox.com/access-tokens
3. Copy the **Default public token** (it starts with `pk.`).
   Or click **Create a token**, give it a name (for example "Salary Tracker"), keep only the **public scopes**
   that are ticked by default, and click **Create token**. Copy it.
4. Do **not** use a secret token (`sk.`). The app never needs one, and the Mapbox download needs no token either.

## 2. Put it in the app

1. In `E:\salary-tracker`, copy `mapbox.properties.example` to `mapbox.properties`.
2. Open `mapbox.properties` and paste your token after the `=`:

   ```
   MAPBOX_ACCESS_TOKEN=pk.your-token-here
   ```

3. Build and install:

   ```bash
   gradlew.bat assembleRelease
   ```

`mapbox.properties` is in `.gitignore`, so it is never uploaded to GitHub. A public token is meant to be inside an app,
but keep it out of the public repository anyway. If a token leaks, delete it on the Tokens page and create a new one.

## 3. Free limits

The free tier covers far more than one person uses (tens of thousands of map loads a month). See
https://www.mapbox.com/pricing. You can see your use on https://account.mapbox.com.

## Privacy

Your saved places stay on your phone (not in Supabase and not sent to the AI). To draw the map, the phone downloads map
pictures from Mapbox for the area you look at, like any map app.
