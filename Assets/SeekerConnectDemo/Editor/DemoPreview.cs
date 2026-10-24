using System.IO;
using UnityEditor;
using UnityEngine;
using UnityEngine.UIElements;

/// <summary>Renders the actual Play-mode UI to a portrait texture for local visual inspection.</summary>
[InitializeOnLoad]
public static class DemoPreview
{
    const string Flag = "SeekerConnect.CapturePreview";
    static RenderTexture target;
    static PanelSettings panel;
    static int frames;
    static DemoPreview() { EditorApplication.update += Update; }
    public static void Capture()
    {
        DemoBuilder.Configure(); SessionState.SetBool(Flag, true);
        EditorApplication.EnterPlaymode();
    }
    static void Update()
    {
        if (!SessionState.GetBool(Flag, false) || !EditorApplication.isPlaying || Time.frameCount < 10) return;
        if (target == null) {
            var document = Object.FindFirstObjectByType<UIDocument>();
            if (document == null || document.panelSettings == null || document.rootVisualElement.panel == null) return;
            target = new RenderTexture(540, 1080, 24); target.Create();
            panel = document.panelSettings; panel.targetTexture = target;
            frames = Time.frameCount;
        }
        if (Time.frameCount - frames < 90) return;
        var previous = RenderTexture.active; RenderTexture.active = target;
        var texture = new Texture2D(540, 1080, TextureFormat.RGB24, false);
        texture.ReadPixels(new Rect(0, 0, 540, 1080), 0, 0); texture.Apply();
        Directory.CreateDirectory("docs/images"); File.WriteAllBytes("docs/images/editor-preview.png", texture.EncodeToPNG());
        RenderTexture.active = previous; panel.targetTexture = null;
        Object.DestroyImmediate(texture); Object.DestroyImmediate(target);
        SessionState.SetBool(Flag, false); EditorApplication.Exit(0);
    }
}
