{ pkgs, ... }:
{
  languages.java = {
    enable = true;
    jdk.package = pkgs.jdk17;
  };

  android = {
    enable = true;
    platforms.version = [ "36" ];
    buildTools.version = [ "35.0.0" "36.0.0" ];
    abis = [ "x86_64" ];
    emulator.enable = true;
    systemImages.enable = true;
    systemImageTypes = [ "google_apis" ];
    googleAPIs.enable = true;
    googleTVAddOns.enable = false;
    ndk.enable = false;
    sources.enable = true;
  };

  packages = [ pkgs.bitwarden-cli pkgs.gradle pkgs.jq pkgs.just ];

  env = {
    VAULTWARDEN_DEV_SIGNING_ITEM = "QOwnNotes Android development signing";
    VAULTWARDEN_SIGNING_ITEM = "QOwnNotes Android release signing";
  };

  enterShell = ''
    echo "Android SDK: $ANDROID_HOME"
    echo "Run 'just' to list build, run, and test recipes."
  '';
}
