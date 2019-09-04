package vsys.blockchain.transaction.assets

import com.google.common.primitives.{Bytes, Longs}
import vsys.blockchain.state.ByteStr
import vsys.utils.base58Length
import play.api.libs.json.{JsObject, Json}
import vsys.account.{Address, PrivateKeyAccount, PublicKeyAccount}
import vsys.utils.crypto.EllipticCurveImpl
import scorex.crypto.encode.Base58
import vsys.utils.serialization.{BytesSerializable, Deser}
import vsys.blockchain.transaction.TransactionParser._
import vsys.blockchain.transaction.{ValidationError, _}

import scala.util.{Failure, Success, Try}

case class TransferTransaction private(assetId: Option[AssetId],
                                       sender: PublicKeyAccount,
                                       recipient: Address,
                                       amount: Long,
                                       timestamp: Long,
                                       feeAssetId: Option[AssetId],
                                       fee: Long,
                                       attachment: Array[Byte],
                                       signature: ByteStr)
  extends SignedTransaction {
  override val transactionType: TransactionType.Value = TransactionType.TransferTransaction

  // TODO
  // add feeScale in assetFee, need to change 100 later
  override val assetFee: (Option[AssetId], Long, Short) = (feeAssetId, fee, 100)

  lazy val toSign: Array[Byte] = {
    val timestampBytes = Longs.toByteArray(timestamp)
    val assetIdBytes = assetId.map(a => (1: Byte) +: a.arr).getOrElse(Array(0: Byte))
    val amountBytes = Longs.toByteArray(amount)
    val feeAssetIdBytes = feeAssetId.map(a => (1: Byte) +: a.arr).getOrElse(Array(0: Byte))
    val feeBytes = Longs.toByteArray(fee)

    Bytes.concat(Array(transactionType.id.toByte),
      sender.publicKey,
      assetIdBytes,
      feeAssetIdBytes,
      timestampBytes,
      amountBytes,
      feeBytes,
      recipient.bytes.arr,
      BytesSerializable.arrayWithSize(attachment))
  }


  override lazy val json: JsObject = jsonBase() ++ Json.obj(
    "recipient" -> recipient.stringRepr,
    "assetId" -> assetId.map(_.base58),
    "amount" -> amount,
    "feeAsset" -> feeAssetId.map(_.base58),
    "attachment" -> Base58.encode(attachment)
  )

  override lazy val bytes: Array[Byte] = Bytes.concat(Array(transactionType.id.toByte), signature.arr, toSign)

}

object TransferTransaction {

  val MaxAttachmentSize = 140
  val MaxAttachmentStringSize = base58Length(MaxAttachmentSize)


  def parseTail(bytes: Array[Byte]): Try[TransferTransaction] = Try {
    import EllipticCurveImpl._

    val signature = ByteStr(bytes.slice(0, SignatureLength))
    val txId = bytes(SignatureLength)
    require(txId == TransactionType.TransferTransaction.id.toByte, s"Signed tx id is not match")
    val sender = PublicKeyAccount(bytes.slice(SignatureLength + 1, SignatureLength + KeyLength + 1))
    val (assetIdOpt, s0) = Deser.parseOption(bytes, SignatureLength + KeyLength + 1, AssetIdLength)
    val (feeAssetIdOpt, s1) = Deser.parseOption(bytes, s0, AssetIdLength)
    val timestamp = Longs.fromByteArray(bytes.slice(s1, s1 + 8))
    val amount = Longs.fromByteArray(bytes.slice(s1 + 8, s1 + 16))
    val feeAmount = Longs.fromByteArray(bytes.slice(s1 + 16, s1 + 24))

    (for {
      recRes <- Address.fromBytes(bytes, s1 + 24)
      (recipient, recipientEnd) = recRes
      (attachment, _) = Deser.parseArraySize(bytes, recipientEnd)
      tt <- TransferTransaction.create(assetIdOpt.map(ByteStr(_)), sender, recipient, amount, timestamp, feeAssetIdOpt.map(ByteStr(_)), feeAmount, attachment, signature)
    } yield tt).fold(left => Failure(new Exception(left.toString)), right => Success(right))
  }.flatten

  def create(assetId: Option[AssetId],
             sender: PublicKeyAccount,
             recipient: Address,
             amount: Long,
             timestamp: Long,
             feeAssetId: Option[AssetId],
             feeAmount: Long,
             attachment: Array[Byte],
             signature: ByteStr): Either[ValidationError, TransferTransaction] = {
    if (attachment.length > TransferTransaction.MaxAttachmentSize) {
      Left(ValidationError.TooBigArray)
    } else if (amount <= 0) {
      Left(ValidationError.NegativeAmount) //CHECK IF AMOUNT IS POSITIVE
    } else if (Try(Math.addExact(amount, feeAmount)).isFailure) {
      Left(ValidationError.OverflowError) // CHECK THAT fee+amount won't overflow Long
    } else if (feeAmount <= 0) {
      Left(ValidationError.InsufficientFee)
    } else {
      Right(TransferTransaction(assetId, sender, recipient, amount, timestamp, feeAssetId, feeAmount, attachment, signature))
    }
  }

  def create(assetId: Option[AssetId],
             sender: PrivateKeyAccount,
             recipient: Address,
             amount: Long,
             timestamp: Long,
             feeAssetId: Option[AssetId],
             feeAmount: Long,
             attachment: Array[Byte]): Either[ValidationError, TransferTransaction] = {
    create(assetId, sender, recipient, amount, timestamp, feeAssetId, feeAmount, attachment, ByteStr.empty).right.map { unsigned =>
      unsigned.copy(signature = ByteStr(EllipticCurveImpl.sign(sender, unsigned.toSign)))
    }
  }
}
