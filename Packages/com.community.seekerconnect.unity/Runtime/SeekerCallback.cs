using UnityEngine;
using UnityEngine.Scripting;

namespace Community.SeekerConnect
{
    [Preserve]
    internal sealed class SeekerCallback : MonoBehaviour
    {
        [Preserve] public void OnNativeResult(string json) => SeekerConnectManager.Receive(json);
        void OnDestroy() => SeekerConnectManager.Closed();
    }
}
