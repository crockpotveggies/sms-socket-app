package com.smssocketapp.gateway

import org.json.JSONObject

object GatewayStatusSlices {
  fun roleState(
    smsRoleGranted: Boolean,
    dialerRoleGranted: Boolean,
  ): JSONObject =
    JSONObject()
      .put("smsRoleGranted", smsRoleGranted)
      .put("dialerRoleGranted", dialerRoleGranted)
}
