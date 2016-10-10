Strava Lap And Split
====================

The application should allow spliting Strava activities and editing lap information in them. One particular use
is to simplify handling of multisport activities like triathlon or duathlon.

Developer notes
---------------

The application project is created in InteliJ IDEA, the project is deployed as Google App Engine.
If you want to deploy your own build, you need to provide:
 - your own Client ID and Client Secret from your own application API registration at https://www.strava.com/settings/api.
 - your own MapBox access token 

Put them in a file `resources/secret.txt`, with an ID and the secret each on its own line, like:

    12356
    47875454gae8974bcd798654
    pk.eyJ1Ijoib3.......
