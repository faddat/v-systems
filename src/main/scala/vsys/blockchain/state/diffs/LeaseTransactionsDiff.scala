package vsys.blockchain.state.diffs

import cats._
import cats.implicits._
import vsys.settings.FunctionalitySettings
import vsys.blockchain.state._
import vsys.blockchain.state.reader.StateReader
import vsys.blockchain.transaction.ValidationError
import vsys.blockchain.transaction.ValidationError.GenericError
import vsys.blockchain.transaction.lease.{LeaseCancelTransaction, LeaseTransaction}

import scala.util.{Left, Right}

object LeaseTransactionsDiff {

  def lease(s: StateReader, height: Int)(tx: LeaseTransaction): Either[ValidationError, Diff] = {
    for {
      proof <- tx.proofs.firstCurveProof
      senderAddr = proof.publicKey.toAddress
      ap <- if (senderAddr == tx.recipient) Left(GenericError("Cannot lease to self"))
        else Right(s.accountPortfolio(senderAddr))
      portfolioDiff <- if (ap.balance - ap.leaseInfo.leaseOut < tx.amount) {
        Left(GenericError(
          s"Cannot lease more than own: Balance:${ap.balance}, already leased: ${ap.leaseInfo.leaseOut}"
        ))
      } else Right(Map(
        senderAddr -> Portfolio(-tx.fee, LeaseInfo(0, tx.amount), Map.empty),
        tx.recipient -> Portfolio(0, LeaseInfo(tx.amount, 0), Map.empty)
      ))
    } yield Diff(height = height, tx = tx, portfolios = portfolioDiff, leaseState = Map(tx.id -> true), chargedFee = tx.fee)
  }

  def leaseCancel(s: StateReader, settings: FunctionalitySettings, time: Long, height: Int)
                 (tx: LeaseCancelTransaction): Either[ValidationError, Diff] = {
    val leaseEi = s.findTransaction[LeaseTransaction](tx.leaseId) match {
      case None => Left(GenericError(s"Related LeaseTransaction not found"))
      case Some(l) => Right(l)
    }
    for {
      lease <- leaseEi
      recipient = lease.recipient
      isLeaseActive = s.isLeaseActive(lease)
      _ <- if (!isLeaseActive)
        Left(GenericError(s"Cannot cancel already cancelled lease")) else Right(())
      leaseProof <- lease.proofs.firstCurveProof
      leaseSender = leaseProof.publicKey
      proof <- tx.proofs.firstCurveProof
      canceller = proof.publicKey
      portfolioDiff <- if (canceller == leaseSender) {
        Right(Monoid.combine(
          Map(canceller.toAddress -> Portfolio(-tx.fee, LeaseInfo(0, -lease.amount), Map.empty)),
          Map(recipient -> Portfolio(0, LeaseInfo(-lease.amount, 0), Map.empty))))
      } else Left(GenericError(s"LeaseTransaction was leased by other sender"))

    } yield Diff(height = height, tx = tx, portfolios = portfolioDiff, leaseState = Map(lease.id -> false), chargedFee = tx.fee)
  }
}
