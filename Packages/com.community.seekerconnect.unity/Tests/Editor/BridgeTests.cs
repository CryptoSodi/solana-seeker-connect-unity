using System;
using System.Threading.Tasks;
using NUnit.Framework;
using UnityEngine;

namespace Community.SeekerConnect.Tests
{
    public sealed class BridgeTests
    {
        [Test] public void GenericMwaRequiresExplicitOptIn()
        { Assert.False(new SeekerConnectConfig().allowMwaTesting); }
        [Test] public void MwaTestingRejectsMainnet()
        { Assert.Throws<ArgumentException>(() => new SeekerConnectConfig { identityUri = "https://example.org", allowMwaTesting = true, chain = "solana:mainnet" }.Validate()); }
        [Test] public void MwaTestingAcceptsDevnet()
        { new SeekerConnectConfig { identityUri = "https://example.org", allowMwaTesting = true }.Validate(); }
        [Test] public async Task OutOfOrderRepliesStayWithTheirRequests()
        {
            var router = new RequestRouter(); var first = router.Add("a"); var second = router.Add("b");
            router.Complete(new NativeReply { id = "b", ok = true, payload = "second" });
            Assert.False(first.IsCompleted); Assert.AreEqual("second", (await second).payload);
            router.Complete(new NativeReply { id = "a", ok = true, payload = "first" });
            Assert.AreEqual("first", (await first).payload); Assert.Zero(router.Count);
        }
        [Test] public void DuplicateAndLateCallbacksAreIgnored()
        {
            var router = new RequestRouter(); router.Add("a");
            Assert.True(router.Complete(new NativeReply { id = "a", ok = true }));
            var next = router.Add("b");
            Assert.False(router.Complete(new NativeReply { id = "a", ok = true })); Assert.False(next.IsCompleted);
        }
        [Test] public void ErrorsPreserveCodesAndReleaseRequests()
        {
            var router = new RequestRouter(); var task = router.Add("a");
            router.Complete(new NativeReply { id = "a", code = "CANCELLED", message = "Cancelled" });
            var error = Assert.ThrowsAsync<SeekerConnectException>(async () => await task);
            Assert.AreEqual("CANCELLED", error.Code); Assert.Zero(router.Count);
        }
        [Test] public void ReceiverLossSettlesPendingTasks()
        {
            var router = new RequestRouter(); var task = router.Add("a"); router.FailAll("CANCELLED");
            Assert.ThrowsAsync<SeekerConnectException>(async () => await task); Assert.Zero(router.Count);
        }
        [TestCase("http://example.org")][TestCase("solana-wallet://example")][TestCase("")]
        public void RejectsInvalidIdentity(string uri)
        { Assert.Throws<ArgumentException>(() => new SeekerConnectConfig { identityUri = uri }.Validate()); }
        [Test] public void AcceptsHttpsAndRelativeIcon()
        { new SeekerConnectConfig { identityUri = "https://example.org", iconPath = "images/icon.png" }.Validate(); }
        [Test] public void ReadsNativeSessionWithoutToken()
        {
            var reply = JsonUtility.FromJson<NativeReply>("{\"id\":\"1\",\"ok\":true,\"hasSession\":true,\"session\":{\"chain\":\"solana:devnet\",\"accounts\":[{\"address\":\"public-address\",\"publicKeyBase64\":\"AQID\"}]}}");
            Assert.True(reply.hasSession); Assert.AreEqual("public-address", reply.session.Account.address);
            CollectionAssert.AreEqual(new byte[] {1,2,3}, reply.session.Account.PublicKey);
        }
    }
}
