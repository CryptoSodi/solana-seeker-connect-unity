mergeInto(LibraryManager.library, {
  SeekerWebRequest: function(receiver, id, operation, json) {
    var target = UTF8ToString(receiver), requestId = UTF8ToString(id);
    var send = function(reply) { SendMessage(target, 'OnNativeResult', reply); };
    if (!globalThis.UnitySeekerConnect) {
      send(JSON.stringify({ id: requestId, ok: false, code: 'BRIDGE_UNAVAILABLE', message: 'Seeker Connect web bundle is missing.' }));
      return;
    }
    globalThis.UnitySeekerConnect.request(requestId, UTF8ToString(operation), UTF8ToString(json), send);
  },
  SeekerWebCancel: function(id) { if (globalThis.UnitySeekerConnect) globalThis.UnitySeekerConnect.cancel(UTF8ToString(id)); }
});
