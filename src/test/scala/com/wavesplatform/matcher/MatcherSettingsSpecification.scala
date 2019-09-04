package com.wavesplatform.matcher

import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import vsys.blockchain.transaction.assets.exchange.AssetPair
import vsys.settings.loadConfig

class MatcherSettingsSpecification extends FlatSpec with Matchers {
  "MatcherSettings" should "read values" in {
    val config = loadConfig(ConfigFactory.parseString(
      """vsys {
        |  directory: "/vsys"
        |  matcher {
        |    enable: yes
        |    account: "BASE58MATCHERACCOUNT"
        |    bind-address: "127.0.0.1"
        |    port: 9925
        |    min-order-fee: 100000
        |    order-match-tx-fee: 100000
        |    snapshots-interval: 1d
        |    max-open-orders: 1000
        |    price-assets: [
        |      "VSYS",
        |      "8LQW8f7P5d5PZM7GtZEBgaqRPGSzS3DfPuiXrURJ4AJS",
        |      "DHgwrRvVyqJsepd32YbBqUeDH4GJ1N984X8QoekjgH8J"
        |    ]
        |    predefined-pairs: [
        |      {amountAsset = "VSYS", priceAsset = "8LQW8f7P5d5PZM7GtZEBgaqRPGSzS3DfPuiXrURJ4AJS"},
        |      {amountAsset = "DHgwrRvVyqJsepd32YbBqUeDH4GJ1N984X8QoekjgH8J", priceAsset = "VSYS"},
        |      {amountAsset = "DHgwrRvVyqJsepd32YbBqUeDH4GJ1N984X8QoekjgH8J", priceAsset = "8LQW8f7P5d5PZM7GtZEBgaqRPGSzS3DfPuiXrURJ4AJS"},
        |    ]
        |    max-timestamp-diff = 3h
        |    blacklisted-assets: []
        |    blacklisted-names: []
        |  }
        |}""".stripMargin))

    val settings = MatcherSettings.fromConfig(config)
    settings.enable should be(true)
    settings.account should be("BASE58MATCHERACCOUNT")
    settings.bindAddress should be("127.0.0.1")
    settings.port should be(9925)
    settings.minOrderFee should be(100000)
    settings.orderMatchTxFee should be(100000)
    settings.journalDataDir should be("/vsys/matcher/journal")
    settings.snapshotsDataDir should be("/vsys/matcher/snapshots")
    settings.snapshotsInterval should be(1.day)
    settings.maxOpenOrders should be(1000)
    settings.priceAssets should be(Seq("VSYS", "8LQW8f7P5d5PZM7GtZEBgaqRPGSzS3DfPuiXrURJ4AJS", "DHgwrRvVyqJsepd32YbBqUeDH4GJ1N984X8QoekjgH8J"))
    settings.predefinedPairs should be(Seq(
      AssetPair.createAssetPair("VSYS", "8LQW8f7P5d5PZM7GtZEBgaqRPGSzS3DfPuiXrURJ4AJS").get,
      AssetPair.createAssetPair("DHgwrRvVyqJsepd32YbBqUeDH4GJ1N984X8QoekjgH8J", "VSYS").get,
      AssetPair.createAssetPair("DHgwrRvVyqJsepd32YbBqUeDH4GJ1N984X8QoekjgH8J", "8LQW8f7P5d5PZM7GtZEBgaqRPGSzS3DfPuiXrURJ4AJS").get
    ))
  }
}
