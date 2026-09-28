{ pkgs, ... }:
{
  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk17;
  };

  android = {
    enable = true;
    # 37.0 is the compile SDK. 36 provides the emulator system image that
    # matches targetSdk and the CI device-test job.
    platforms.version = [ "36" "37.0" ];
    # The first version provides the aapt2 override. AGP 9 release packaging
    # silently omits the manifest and resources with build-tools 35 aapt2.
    buildTools.version = [ "36.0.0" "35.0.0" ];
    abis = [ "x86_64" ];
    emulator.enable = true;
    systemImages.enable = true;
    systemImageTypes = [ "google_apis" ];
    googleAPIs.enable = true;
    googleTVAddOns.enable = false;
    ndk.enable = false;
    sources.enable = true;
  };

  packages = [ pkgs.apksigcopier pkgs.bitwarden-cli pkgs.gradle pkgs.jq pkgs.just ];

  env = {
    VAULTWARDEN_DEV_SIGNING_ITEM = "QOwnNotes Android development signing";
    VAULTWARDEN_SIGNING_ITEM = "QOwnNotes Android release signing";
  };

  enterShell = ''
    echo "Android SDK: $ANDROID_HOME"
    echo "Run 'just' to list build, run, and test recipes."
  '';
}
