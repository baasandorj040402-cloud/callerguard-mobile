package com.erdene.callerinsight.data

import com.erdene.callerinsight.Constants

object AppGraph {
  val client = CallerInsightClient(Constants.BACKEND_URL)
  val repo = CallerInsightRepository(client)
}
