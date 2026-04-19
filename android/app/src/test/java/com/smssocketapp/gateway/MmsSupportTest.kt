package com.smssocketapp.gateway

import org.junit.Assert.assertTrue
import org.junit.Test

class MmsSupportTest {
  @Test
  fun `supported mime types include common mms media`() {
    assertTrue(MmsSupport.isSupportedMimeType("image/png"))
    assertTrue(MmsSupport.isSupportedMimeType("video/mp4"))
    assertTrue(MmsSupport.isSupportedMimeType("audio/mpeg"))
    assertTrue(MmsSupport.isSupportedMimeType("application/pdf"))
    assertTrue(!MmsSupport.isSupportedMimeType("application/zip"))
  }

  @Test
  fun `supported mime type check is case and whitespace tolerant`() {
    assertTrue(MmsSupport.isSupportedMimeType(" IMAGE/JPEG "))
    assertTrue(MmsSupport.isSupportedMimeType(" Audio/Wav "))
    assertTrue(!MmsSupport.isSupportedMimeType("   "))
  }
}
