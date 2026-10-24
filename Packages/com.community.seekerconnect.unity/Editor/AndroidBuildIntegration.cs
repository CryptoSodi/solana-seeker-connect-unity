#if UNITY_ANDROID
using System;
using System.IO;
using UnityEditor;
using UnityEditor.Android;
using UnityEditor.Build;
using UnityEditor.PackageManager;

namespace Community.SeekerConnect.Editor
{
    /// <summary>Compiles native source inside the exported Gradle build, retaining Maven transitive dependencies.</summary>
    public sealed class AndroidBuildIntegration : IPostGenerateGradleAndroidProject
    {
        public int callbackOrder => 500;
        public void OnPostGenerateGradleAndroidProject(string path)
        {
            var root = Directory.GetParent(path).FullName;
            var package = UnityEditor.PackageManager.PackageInfo.FindForAssembly(typeof(AndroidBuildIntegration).Assembly);
            var source = Path.Combine(package.resolvedPath, "Native~");
            var target = Path.Combine(root, "seekerBridge");
            CopySources(source, target);
            var rootBuild = File.ReadAllText(Path.Combine(root, "build.gradle"));
            // Unity 6.6's bundled AGP 9.0 / Gradle 9.1 are verified. Older editors need the export route.
            if (!rootBuild.Contains("version '9.0.0'") && !rootBuild.Contains("version '9."))
                throw new BuildFailedException("Seeker Connect requires the documented Gradle 9.1 / AGP 9.0 toolchain. Use Unity 6000.6.0f1 or configure compatible custom Gradle templates before building.");
            if (!rootBuild.Contains("org.jetbrains.kotlin.android"))
                File.WriteAllText(Path.Combine(root, "build.gradle"), rootBuild.Replace("plugins {", "plugins {\n    id 'org.jetbrains.kotlin.android' version '2.4.10' apply false"));
            AppendOnce(Path.Combine(root, "settings.gradle"), "include ':seekerBridge'", "\ninclude ':seekerBridge'\n");
            AppendOnce(Path.Combine(path, "build.gradle"), "project(':seekerBridge')", "\ndependencies { implementation project(':seekerBridge') }\n");
            AppendOnce(Path.Combine(root, "gradle.properties"), "# Seeker Connect", "\n# Seeker Connect\nandroid.useAndroidX=true\nandroid.builtInKotlin=false\nandroid.newDsl=false\n");
            // Android backup must not export the SDK's cached authorization tokens.
            var manifestPath = Path.Combine(path, "src/main/AndroidManifest.xml");
            var document = new System.Xml.XmlDocument(); document.Load(manifestPath);
            var application = document.SelectSingleNode("/manifest/application") as System.Xml.XmlElement;
            if (application == null) throw new BuildFailedException("No application node in Unity manifest");
            application.SetAttribute("allowBackup", "http://schemas.android.com/apk/res/android", "false");
            application.SetAttribute("dataExtractionRules", "http://schemas.android.com/apk/res/android", "@xml/seeker_backup_rules");
            application.SetAttribute("fullBackupContent", "http://schemas.android.com/apk/res/android", "@xml/seeker_backup_legacy");
            document.Save(manifestPath);
        }
        static void AppendOnce(string path, string marker, string content)
        { if (!File.ReadAllText(path).Contains(marker)) File.AppendAllText(path, content); }
        static void CopySources(string source, string target)
        {
            Directory.CreateDirectory(target);
            foreach (var file in Directory.GetFiles(source))
                if (!file.EndsWith(".meta")) File.Copy(file, Path.Combine(target, Path.GetFileName(file)), true);
            foreach (var directory in Directory.GetDirectories(source)) {
                var name = Path.GetFileName(directory);
                if (name == "build" || name == ".gradle") continue;
                CopySources(directory, Path.Combine(target, name));
            }
        }
    }
}
#endif
