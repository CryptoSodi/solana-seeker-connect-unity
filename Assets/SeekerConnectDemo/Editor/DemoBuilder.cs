using System.IO;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;
using UnityEditor.SceneManagement;
using UnityEngine;
using UnityEngine.UIElements;

public static class DemoBuilder
{
    const string ScenePath = "Assets/SeekerConnectDemo/Scenes/SeekerConnectDemo.unity";
    [MenuItem("Seeker Connect/Configure Demo")]
    public static void Configure()
    {
        Directory.CreateDirectory("Assets/SeekerConnectDemo/Scenes");
        // NewScene may unload assets from the previous scene. Load panel assets afterwards.
        var scene = EditorSceneManager.NewScene(NewSceneSetup.EmptyScene, NewSceneMode.Single);
        var panel = AssetDatabase.LoadAssetAtPath<PanelSettings>("Assets/SeekerConnectDemo/UI/DemoPanel.asset");
        if (panel == null) {
            panel = ScriptableObject.CreateInstance<PanelSettings>();
            panel.scaleMode = PanelScaleMode.ScaleWithScreenSize; panel.referenceResolution = new Vector2Int(540, 1080); panel.match = 0;
            AssetDatabase.CreateAsset(panel, "Assets/SeekerConnectDemo/UI/DemoPanel.asset");
        }
        var ui = new GameObject("Seeker Connect — Unity Demo");
        var document = ui.AddComponent<UIDocument>();
        document.panelSettings = panel;
        if (document.panelSettings == null) throw new BuildFailedException("Demo UI panel is missing");
        EditorUtility.SetDirty(document);
        ui.AddComponent<SeekerDemo>().theme = AssetDatabase.LoadAssetAtPath<StyleSheet>("Assets/SeekerConnectDemo/UI/Demo.uss");
        EditorSceneManager.SaveScene(scene, ScenePath);
        EditorBuildSettings.scenes = new[] { new EditorBuildSettingsScene(ScenePath, true) };
        PlayerSettings.companyName = "Community"; PlayerSettings.productName = "Seeker Connect Unity Demo";
        PlayerSettings.bundleVersion = "0.1.2";
        PlayerSettings.Android.bundleVersionCode = 3;
        PlayerSettings.SetApplicationIdentifier(NamedBuildTarget.Android, "com.community.seekerconnect.demo");
        PlayerSettings.SetScriptingBackend(NamedBuildTarget.Android, ScriptingImplementation.IL2CPP);
        PlayerSettings.Android.targetArchitectures = AndroidArchitecture.ARM64;
        PlayerSettings.Android.minSdkVersion = (AndroidSdkVersions)26;
        PlayerSettings.Android.targetSdkVersion = (AndroidSdkVersions)36;
        PlayerSettings.Android.applicationEntry = AndroidApplicationEntry.Activity;
        PlayerSettings.defaultInterfaceOrientation = UIOrientation.Portrait;
        PlayerSettings.runInBackground = true;
        AssetDatabase.SaveAssets();
    }
    [MenuItem("Seeker Connect/Build Android APK")]
    public static void BuildApk()
    {
        Configure(); Directory.CreateDirectory("Builds");
        EditorUserBuildSettings.exportAsGoogleAndroidProject = false;
        var report = BuildPipeline.BuildPlayer(new BuildPlayerOptions { scenes = new[] { ScenePath }, locationPathName = "Builds/SeekerConnectDemo.apk", target = BuildTarget.Android, options = BuildOptions.Development });
        if (report.summary.result != BuildResult.Succeeded) throw new BuildFailedException("Android APK failed: " + report.summary.result);
    }
    [MenuItem("Seeker Connect/Build WebGL")]
    public static void BuildWebGL()
    {
        Configure();
        PlayerSettings.WebGL.compressionFormat = WebGLCompressionFormat.Disabled;
        PlayerSettings.WebGL.template = "PROJECT:Seeker";
        var report = BuildPipeline.BuildPlayer(new BuildPlayerOptions { scenes = new[] { ScenePath }, locationPathName = "Builds/WebGL", target = BuildTarget.WebGL, options = BuildOptions.None });
        if (report.summary.result != BuildResult.Succeeded) throw new BuildFailedException("WebGL build failed: " + report.summary.result);
    }
    [MenuItem("Seeker Connect/Export Android Gradle Project")]
    public static void Export()
    {
        Configure(); EditorUserBuildSettings.exportAsGoogleAndroidProject = true;
        var report = BuildPipeline.BuildPlayer(new BuildPlayerOptions { scenes = new[] { ScenePath }, locationPathName = "Builds/AndroidExport", target = BuildTarget.Android, options = BuildOptions.Development });
        if (report.summary.result != BuildResult.Succeeded) throw new BuildFailedException("Android export failed");
    }
}
