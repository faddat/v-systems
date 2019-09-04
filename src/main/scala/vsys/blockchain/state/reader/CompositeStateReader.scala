package vsys.blockchain.state.reader

import java.util.concurrent.locks.ReentrantReadWriteLock

import cats.implicits._
import cats.kernel.Monoid
import vsys.blockchain.state._
import vsys.blockchain.transaction.TransactionParser.TransactionType
import vsys.account.Address
import vsys.blockchain.transaction.ProcessedTransaction
import vsys.blockchain.transaction.lease.LeaseTransaction
import vsys.blockchain.contract.{Contract, DataEntry}


class CompositeStateReader(inner: StateReader, blockDiff: BlockDiff) extends StateReader {

  def synchronizationToken: ReentrantReadWriteLock = inner.synchronizationToken

  private val txDiff = blockDiff.txsDiff

  override def transactionInfo(id: ByteStr): Option[(Int, ProcessedTransaction)] =
    txDiff.transactions.get(id)
      .map(t => (t._1, t._2))
      .orElse(inner.transactionInfo(id))

  override def accountPortfolio(a: Address): Portfolio =
    inner.accountPortfolio(a).combine(txDiff.portfolios.get(a).orEmpty)

  override def assetInfo(id: ByteStr): Option[AssetInfo] = (inner.assetInfo(id), txDiff.issuedAssets.get(id)) match {
    case (None, None) => None
    case (existing, upd) => Some(existing.orEmpty.combine(upd.orEmpty))
  }

  override def height: Int = inner.height + blockDiff.heightDiff

  // TODO: return Option[Address] instead of Option[String]
  override def slotAddress(id: Int): Option[String] = txDiff.slotids.getOrElse(id, inner.slotAddress(id))

  // TODO: param as Address instead of String
  override def addressSlot(add: String): Option[Int] = txDiff.addToSlot.getOrElse(add, inner.addressSlot(add))

  override def effectiveSlotAddressSize: Int = inner.effectiveSlotAddressSize + txDiff.slotNum

  override def accountTransactionIds(a: Address, limit: Int, offset: Int): (Int, Seq[ByteStr]) = {
    val fromDiffOrg = txDiff.accountTransactionIds.get(a).orEmpty
    val offsetNew = scala.math.max(0, offset - fromDiffOrg.length)
    val fromDiff = fromDiffOrg.drop(offset)
    if (fromDiff.length >= limit) {
      (fromDiffOrg.size + inner.accountTransactionsLengths(a), fromDiff.take(limit))
    } else {
      val fromState = inner.accountTransactionIds(a, limit - fromDiff.size, offsetNew)
      (fromDiffOrg.size + fromState._1, fromDiff ++ fromState._2) // fresh head ++ stale tail
    }
  }

  override def txTypeAccountTxIds(txType: TransactionType.Value, a: Address, limit: Int, offset: Int): (Int, Seq[ByteStr]) = {
    val fromDiffOrg = txDiff.txTypeAccountTxIds.get((txType, a)).orEmpty
    val offsetNew = scala.math.max(0, offset - fromDiffOrg.length)
    val fromDiff = fromDiffOrg.drop(offset)
    if (fromDiff.length >= limit) {
      (fromDiffOrg.size + inner.txTypeAccTxLengths(txType, a), fromDiff.take(limit))
    } else {
      val fromState = inner.txTypeAccountTxIds(txType, a, limit - fromDiff.size, offsetNew)
      (fromDiffOrg.size + fromState._1, fromDiff ++ fromState._2)
    }
  }

  override def accountTransactionsLengths(a: Address): Int = {
    txDiff.accountTransactionIds.get(a).orEmpty.size + inner.accountTransactionsLengths(a)
  }

  override def txTypeAccTxLengths(txType: TransactionType.Value, a: Address): Int = {
    txDiff.txTypeAccountTxIds.get((txType, a)).orEmpty.size + inner.txTypeAccTxLengths(txType, a)
  }

  override def snapshotAtHeight(acc: Address, h: Int): Option[Snapshot] =
    blockDiff.snapshots.get(acc).flatMap(_.get(h)).orElse(inner.snapshotAtHeight(acc, h))

  override def contractContent(id: ByteStr): Option[(Int, ByteStr, Contract)] =
    txDiff.contracts.get(id)
      .map(t => (t._1, t._2, t._3))
      .orElse(inner.contractContent(id))

  override def contractInfo(id: ByteStr): Option[DataEntry] =
    txDiff.contractDB.get(id)
      .map(t => DataEntry.fromBytes(t).explicitGet())
      .orElse(inner.contractInfo(id))

  override def contractTokens(id: ByteStr): Int = inner.contractTokens(id) + txDiff.contractTokens.getOrElse(id, 0)

  override def tokenInfo(id: ByteStr): Option[DataEntry] = {
    txDiff.tokenDB.get(id)
      .map(t => DataEntry.fromBytes(t).explicitGet())
      .orElse(inner.tokenInfo(id))
  }

  override def tokenAccountBalance(id: ByteStr): Long =
    safeSum(txDiff.tokenAccountBalance.getOrElse(id, 0L), inner.tokenAccountBalance(id))

  override def dbGet(key: ByteStr): Option[ByteStr] =
    txDiff.dbEntries.get(key).map(v=>v.bytes)
      .orElse(inner.dbGet(key))

  override def accountPortfolios: Map[Address, Portfolio] = Monoid.combine(inner.accountPortfolios, txDiff.portfolios)

  override def isLeaseActive(leaseTx: LeaseTransaction): Boolean =
    blockDiff.txsDiff.leaseState.getOrElse(leaseTx.id, inner.isLeaseActive(leaseTx))

  override def activeLeases(): Seq[ByteStr] = {
    blockDiff.txsDiff.leaseState.collect { case (id, isActive) if isActive => id }
      .toSeq ++ inner.activeLeases()
      .collect { case id if blockDiff.txsDiff.leaseState.getOrElse(id, true) => id
      }
  }

  override def lastUpdateHeight(acc: Address): Option[Int] = blockDiff.snapshots.get(acc).map(_.lastKey).orElse(inner.lastUpdateHeight(acc))

  override def lastUpdateWeightedBalance(acc: Address): Option[Long] = blockDiff.snapshots.get(acc).map(_.last._2.weightedBalance).orElse(inner.lastUpdateWeightedBalance(acc))

  override def containsTransaction(id: ByteStr): Boolean = blockDiff.txsDiff.transactions.contains(id) || inner.containsTransaction(id)

  override def filledVolumeAndFee(orderId: ByteStr): OrderFillInfo =
    blockDiff.txsDiff.orderFills.get(orderId).orEmpty.combine(inner.filledVolumeAndFee(orderId))
}

object CompositeStateReader {

  class Proxy(val inner: StateReader, blockDiff: () => BlockDiff) extends StateReader {

    override def synchronizationToken: ReentrantReadWriteLock = inner.synchronizationToken

    override def accountPortfolio(a: Address): Portfolio =
      new CompositeStateReader(inner, blockDiff()).accountPortfolio(a)

    override def accountTransactionIds(a: Address, limit: Int, offset: Int): (Int, Seq[ByteStr]) =
      new CompositeStateReader(inner, blockDiff()).accountTransactionIds(a, limit, offset)

    override def txTypeAccountTxIds(txType: TransactionType.Value, a: Address, limit: Int, offset: Int): (Int, Seq[ByteStr]) =
      new CompositeStateReader(inner, blockDiff()).txTypeAccountTxIds(txType, a, limit, offset)

    override def accountTransactionsLengths(a: Address): Int =
      new CompositeStateReader(inner, blockDiff()).accountTransactionsLengths(a)

    override def txTypeAccTxLengths(txType: TransactionType.Value, a: Address): Int =
      new CompositeStateReader(inner, blockDiff()).txTypeAccTxLengths(txType, a)

    override def accountPortfolios: Map[Address, Portfolio] =
      new CompositeStateReader(inner, blockDiff()).accountPortfolios

    override def transactionInfo(id: ByteStr): Option[(Int, ProcessedTransaction)] =
      new CompositeStateReader(inner, blockDiff()).transactionInfo(id)

    override def contractContent(id: ByteStr): Option[(Int, ByteStr, Contract)] =
      new CompositeStateReader(inner, blockDiff()).contractContent(id)

    override def contractInfo(id: ByteStr): Option[DataEntry] =
      new CompositeStateReader(inner, blockDiff()).contractInfo(id)

    override def contractTokens(id: ByteStr): Int =
      new CompositeStateReader(inner, blockDiff()).contractTokens(id)

    override def tokenInfo(id: ByteStr): Option[DataEntry] =
      new CompositeStateReader(inner, blockDiff()).tokenInfo(id)

    override def tokenAccountBalance(id: ByteStr): Long =
      new CompositeStateReader(inner, blockDiff()).tokenAccountBalance(id)

    override def dbGet(key: ByteStr): Option[ByteStr] =
      new CompositeStateReader(inner, blockDiff()).dbGet(key)

    override def assetInfo(id: ByteStr): Option[AssetInfo] =
      new CompositeStateReader(inner, blockDiff()).assetInfo(id)

    override def height: Int =
      new CompositeStateReader(inner, blockDiff()).height

    override def slotAddress(id: Int): Option[String] =
      new CompositeStateReader(inner,blockDiff()).slotAddress(id)

    override def effectiveSlotAddressSize: Int =
      new CompositeStateReader(inner,blockDiff()).effectiveSlotAddressSize

    override def addressSlot(add: String): Option[Int] =
      new CompositeStateReader(inner,blockDiff()).addressSlot(add)

    override def isLeaseActive(leaseTx: LeaseTransaction): Boolean =
      new CompositeStateReader(inner, blockDiff()).isLeaseActive(leaseTx)

    override def activeLeases(): Seq[ByteStr] =
      new CompositeStateReader(inner, blockDiff()).activeLeases()

    override def lastUpdateHeight(acc: Address): Option[Int] =
      new CompositeStateReader(inner, blockDiff()).lastUpdateHeight(acc)

    override def lastUpdateWeightedBalance(acc: Address): Option[Long] =
      new CompositeStateReader(inner, blockDiff()).lastUpdateWeightedBalance(acc)

    override def snapshotAtHeight(acc: Address, h: Int): Option[Snapshot] =
      new CompositeStateReader(inner, blockDiff()).snapshotAtHeight(acc, h)

    override def containsTransaction(id: ByteStr): Boolean =
      new CompositeStateReader(inner, blockDiff()).containsTransaction(id)

    override def filledVolumeAndFee(orderId: ByteStr): OrderFillInfo =
      new CompositeStateReader(inner, blockDiff()).filledVolumeAndFee(orderId)
  }

  def composite(inner: StateReader, blockDiff: () => BlockDiff): Proxy = new Proxy(inner, blockDiff)
}
