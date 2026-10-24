using System.Collections.Generic;
using System.Threading.Tasks;

namespace Community.SeekerConnect
{
    // Confined to Unity's main thread. Late or duplicate callbacks never complete a newer request.
    internal sealed class RequestRouter
    {
        readonly Dictionary<string, TaskCompletionSource<NativeReply>> pending = new Dictionary<string, TaskCompletionSource<NativeReply>>();
        public int Count => pending.Count;
        public Task<NativeReply> Add(string id)
        {
            var source = new TaskCompletionSource<NativeReply>(TaskCreationOptions.RunContinuationsAsynchronously);
            pending.Add(id, source);
            return source.Task;
        }
        public bool Contains(string id) => id != null && pending.ContainsKey(id);
        public bool Complete(NativeReply reply)
        {
            if (!Contains(reply.id)) return false;
            var source = pending[reply.id]; pending.Remove(reply.id);
            if (reply.ok) source.TrySetResult(reply);
            else source.TrySetException(new SeekerConnectException(reply.code ?? "WALLET_ERROR", reply.message ?? "Wallet operation failed"));
            return true;
        }
        public void FailAll(string code)
        {
            foreach (var source in pending.Values) source.TrySetException(new SeekerConnectException(code, "Unity callback receiver closed"));
            pending.Clear();
        }
    }
}
