package vsys.blockchain.transaction

import vsys.utils.base58Length
import vsys.blockchain.transaction.assets._
import vsys.blockchain.transaction.assets.exchange.ExchangeTransaction
import vsys.blockchain.transaction.contract.{ExecuteContractFunctionTransaction, RegisterContractTransaction}
import vsys.blockchain.transaction.database.DbPutTransaction
import vsys.blockchain.transaction.lease.{LeaseCancelTransaction, LeaseTransaction}
import vsys.blockchain.transaction.spos.{ContendSlotsTransaction, ReleaseSlotsTransaction}

import scala.util.{Failure, Try}

object TransactionParser {

  object TransactionType extends Enumeration {
    val GenesisTransaction = Value(1)
    val PaymentTransaction = Value(2)
    val LeaseTransaction = Value(3)
    val LeaseCancelTransaction = Value(4)
    val MintingTransaction = Value(5)
    val ContendSlotsTransaction = Value(6)
    val ReleaseSlotsTransaction = Value(7)
    val RegisterContractTransaction = Value(8)
    val ExecuteContractFunctionTransaction = Value(9)
    val DbPutTransaction = Value(10)
    val IssueTransaction = Value(11)
    val TransferTransaction = Value(12)
    val ReissueTransaction = Value(13)
    val BurnTransaction = Value(14)
    val ExchangeTransaction = Value(15)
  }

  val TimestampLength = 8
  val AmountLength = 8
  val TypeLength = 1
  val SignatureLength = 64
  val SignatureStringLength: Int = base58Length(SignatureLength)
  val KeyLength = 32
  val SlotIdLength = 4
  val DefaultFeeScale: Short = 100
  val KeyStringLength: Int = base58Length(KeyLength)

  def parseBytes(data: Array[Byte]): Try[Transaction] =
    data.head match {
      case txType: Byte if txType == TransactionType.GenesisTransaction.id =>
        GenesisTransaction.parseTail(data.tail)

      case txType: Byte if txType == TransactionType.PaymentTransaction.id =>
        PaymentTransaction.parseTail(data.tail)

      case txType: Byte if txType == TransactionType.IssueTransaction.id =>
        IssueTransaction.parseTail(data.tail)

      case txType: Byte if txType == TransactionType.TransferTransaction.id =>
        TransferTransaction.parseTail(data.tail)

      case txType: Byte if txType == TransactionType.ReissueTransaction.id =>
        ReissueTransaction.parseTail(data.tail)

      case txType: Byte if txType == TransactionType.BurnTransaction.id =>
        BurnTransaction.parseTail(data.tail)

      case txType: Byte if txType == TransactionType.ExchangeTransaction.id =>
        ExchangeTransaction.parseTail(data.tail)

      case txType: Byte if txType == TransactionType.LeaseTransaction.id =>
        LeaseTransaction.parseTail(data.tail)

      case txType: Byte if txType == TransactionType.LeaseCancelTransaction.id =>
        LeaseCancelTransaction.parseTail(data.tail)
      
      case txType: Byte if txType == TransactionType.MintingTransaction.id =>
        MintingTransaction.parseTail(data.tail)
      
      case txType: Byte if txType == TransactionType.ContendSlotsTransaction.id =>
        ContendSlotsTransaction.parseTail(data.tail)

      case txType: Byte if txType == TransactionType.ReleaseSlotsTransaction.id =>
        ReleaseSlotsTransaction.parseTail(data.tail)

      case txType: Byte if txType == TransactionType.RegisterContractTransaction.id =>
        RegisterContractTransaction.parseTail(data.tail)

      case txType: Byte if txType == TransactionType.ExecuteContractFunctionTransaction.id =>
        ExecuteContractFunctionTransaction.parseTail(data.tail)

      case txType: Byte if txType == TransactionType.DbPutTransaction.id =>
        DbPutTransaction.parseTail(data.tail)

      case txType => Failure(new Exception(s"Invalid transaction type: $txType"))
    }
}
