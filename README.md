# helse-spinder
Avstemming/Matching mellom spa sykepengevedtak og infotrygdvedtak




##### For å resette kafka-offset tilbake til start (på NAIS), for å kunne kjøre avstemming på nytt:

Legg inn "RESET_STREAM_ONLY.env": "RESET_STREAM_ONLY=true" som secret i vault for spinder.

Finn pod'en (må være kun én pod):

`kubectl get pods | grep spinder`

Eks. utputt: `spinder-11111111111-yyyy                                 1/1     Running            0          4m`

Slett pod´en:

`kubectl delete pod spinder-11111111111-yyyy`

Finn den nye pod´en som automatisk blir kjørt opp, og sjekk loggene:

`kubectl logs spinder-22222222222-zzzz`

Høyst sannsynlig ser du feilmeldingen `InconsistentGroupProtocolException`  

Gjør i så tilfelle det samme en gang til.
Forhåpentligvis ser du denne gang logg-meldingen:
`SpinderStreamResetter - mission accomplished (Fjern env.RESET_STREAM_ONLY og start appen på nytt)`

Så fjern da vault-hemmeligheten, eller sett verdien til "RESET_STREAM_ONLY=false".

Slett pod'en en tredje gang, og den fyres opp på nytt og skal kjøre ny avstemming fra start.
