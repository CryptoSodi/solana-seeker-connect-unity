using System.Collections;
using System.Linq;
using Community.SeekerConnect;
using NUnit.Framework;
using UnityEngine;
using UnityEngine.SceneManagement;
using UnityEngine.TestTools;
using UnityEngine.UIElements;

public sealed class DemoTests
{
    [UnityTest]
    public IEnumerator DemoSceneHasPanelAndAllWalletActions()
    {
        yield return SceneManager.LoadSceneAsync("SeekerConnectDemo");
        yield return null;
        var document = Object.FindFirstObjectByType<UIDocument>();
        Assert.NotNull(document);
        Assert.NotNull(document.panelSettings, "A missing panel makes the otherwise buildable APK blank.");
        Assert.NotNull(document.rootVisualElement.panel);
        var labels = document.rootVisualElement.Query<Button>().ToList().Select(button => button.text).ToArray();
        CollectionAssert.IsSubsetOf(new[] {
#if UNITY_WEBGL
            "Connect with Seeker Connect",
#else
            "Connect with MWA test wallet",
#endif
            "Restore Session", "Copy wallet address", "Disconnect", "Sign Message", "Build Devnet memo", "Request transaction signature" }, labels);
    }

    [UnityTest]
    public IEnumerator EditorNeverReportsASimulatedWalletConnection()
    {
#if UNITY_EDITOR
        var task = SeekerConnectManager.InitializeAsync(new SeekerConnectConfig { identityUri = "https://example.org" });
        while (!task.IsCompleted) yield return null;
        Assert.True(task.IsFaulted);
        Assert.AreEqual("UNSUPPORTED_PLATFORM", (task.Exception.InnerException as SeekerConnectException)?.Code);
        Assert.IsNull(SeekerConnectManager.Session);
#else
        Assert.Ignore("This test checks the Editor platform guard only.");
        yield break;
#endif
    }
}
