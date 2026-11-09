# R8's default optimized rules plus the keep rules the libraries ship are
# enough: Room, CameraX and ML Kit all bring their own. Except:

# ML Kit finds its registrars by name in the manifest and creates them by
# reflection. firebase-components keeps the classes, but under R8's full mode
# a -keep without members drops their no-argument constructor: ML Kit then
# registers nothing, and BarcodeScanning.getClient crashes the moment the
# scanner opens.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
