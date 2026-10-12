# L-AD-blocker

Reklamblockerare för Android som tar bort reklam i **hela telefonen** – i appar, spel och
webbläsare – utan root.

## Så fungerar det

Appen startar en lokal VPN som *bara* tar emot telefonens DNS-uppslag (när en app frågar
"vilken IP-adress har `ads.doubleclick.net`?"). Uppslag mot kända reklam- och spårningsservrar
besvaras direkt på telefonen med `0.0.0.0`, så reklamen kan aldrig laddas. Alla andra uppslag
skickas vidare till en vanlig DNS-server. Ingen annan trafik går genom appen och inget skickas
till någon extern VPN-server, så surfhastighet och batteri påverkas inte märkbart.

- Blocklistor: HaGeZi Multi PRO, StevenBlack och AdAway (på som standard), samt valfritt
  tillverkarnas spårning (Samsung, Xiaomi m.fl.) och OISD Big. Uppdateras automatiskt var tredje dag.
- Inbyggd grundlista som fungerar direkt, även innan listorna laddats ner.
- Spel- och appreklam: AdMob, Unity Ads, AppLovin, ironSource, Vungle, Chartboost, Facebook m.fl.
- Fångar appar som försöker gå förbi telefonens DNS genom att fråga 8.8.8.8, 1.1.1.1 osv. direkt.
- Logg över senaste uppslag – tryck på en domän för att tillåta eller blockera den.
- Egen vitlista och egen blocklista.
- Startar automatiskt efter omstart.

## YouTube – viktigt att veta

YouTube-appen hämtar reklamen från **samma servrar som själva videorna**. Därför kan ingen
blockerare av den här typen (inte heller Blokada, AdGuard DNS, NextDNS osv.) ta bort reklam i
YouTube-appen utan att videorna slutar fungera. Det som fungerar:

- **Firefox + tillägget uBlock Origin**, och titta på youtube.com i webbläsaren.
- Reklamfria YouTube-klienter som **NewPipe** (finns på F-Droid) eller **ReVanced**.
- YouTube Premium.

## Installera på telefonen

1. Öppna repots **Releases**-sida på GitHub i telefonens webbläsare och ladda ner
   `L-AD-blocker.apk` från den senaste releasen.
2. Öppna filen. Android frågar om du vill tillåta installation från webbläsaren – tillåt det.
3. Öppna appen och tryck **Starta reklamblockering**. Godkänn VPN-förfrågan.
4. Rekommenderat:
   - *Inställningar → Nätverk och internet → VPN → L-AD-blocker → Alltid på-VPN.*
   - Chrome: stäng av *Använd säker DNS* (Inställningar → Integritet och säkerhet).
   - Androids *Privat DNS*: sätt till *Av* eller *Automatiskt*.

Fungerar på Android 7.0 och senare. Android tillåter bara en VPN åt gången.

*iPhone stöds inte – iOS tillåter inte den här typen av appar utan ett betalt Apple-utvecklarkonto.
På iPhone kan du i stället installera en DNS-profil från t.ex. AdGuard DNS eller NextDNS.*

## Bygga

APK:n byggs automatiskt av GitHub Actions vid varje push (`.github/workflows/build.yml`) och
publiceras som en release. Lokalt (kräver Android SDK):

```
./gradlew testReleaseUnitTest assembleRelease
```

APK:n signeras med nyckeln i `app/signing/` så att nya versioner kan installeras ovanpå gamla.
Nyckeln är bara till för att du själv ska kunna installera uppdateringar – vill du använda en
egen, sätt miljövariablerna `SIGNING_KEYSTORE_FILE`, `SIGNING_STORE_PASSWORD`,
`SIGNING_KEY_ALIAS` och `SIGNING_KEY_PASSWORD`.
