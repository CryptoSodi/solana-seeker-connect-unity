using System.IO;
using UnityEditor;
using UnityEditor.Build;
using UnityEditor.Build.Reporting;

namespace Community.SeekerConnect.Editor
{
    // Included with the UPM package so consumers do not need a custom WebGL template.
    public sealed class WebBuild : IPostprocessBuildWithReport
    {
        public int callbackOrder => 1000;
        public void OnPostprocessBuild(BuildReport report)
        {
            if (report.summary.platform != BuildTarget.WebGL) return;
            var package = UnityEditor.PackageManager.PackageInfo.FindForAssembly(typeof(WebBuild).Assembly);
            var source = Path.Combine(package.resolvedPath, "Web~/seeker-connect.js");
            if (!File.Exists(source)) throw new BuildFailedException("Build the official web SDK bundle with npm ci && npm run build in web/ first.");
            var output = report.summary.outputPath;
            File.Copy(source, Path.Combine(output, "seeker-connect.js"), true);
            File.Copy(Path.Combine(package.resolvedPath, "Web~/THIRD_PARTY_LICENSES.txt"), Path.Combine(output, "seeker-connect-licenses.txt"), true);
            var index = Path.Combine(output, "index.html");
            var html = File.ReadAllText(index);
            if (!html.Contains("</head>")) throw new BuildFailedException("WebGL template needs a closing head tag.");
            File.WriteAllText(index, html.Replace("</head>", "<script src=\"seeker-connect.js\"></script>\n</head>"));
        }
    }
}
