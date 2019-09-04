package vsys.blockchain.state.diffs

import vsys.blockchain.contract.ExecutionContext
import vsys.blockchain.state.opcdiffs.OpcFuncDiffer
import vsys.blockchain.state.reader.StateReader
import vsys.blockchain.state.{Diff, LeaseInfo, Portfolio}
import vsys.blockchain.transaction.contract.ExecuteContractFunctionTransaction
import vsys.blockchain.transaction.{TransactionStatus, ValidationError}
import vsys.blockchain.transaction.ValidationError._

object ExecuteContractFunctionTransactionDiff {
  def apply(s: StateReader, height: Int)(tx: ExecuteContractFunctionTransaction): Either[ValidationError, Diff] = {
    tx.proofs.firstCurveProof.flatMap( proof => {
      val senderAddress = proof.publicKey.toAddress
      ( for {
        exContext <- ExecutionContext.fromExeConTx(s, height, tx)
        diff <- OpcFuncDiffer(exContext)(tx.data)
      } yield Diff(
        height = height,
        tx = tx,
        portfolios = Map(senderAddress -> Portfolio(-tx.fee, LeaseInfo.empty, Map.empty)),
        tokenDB = diff.tokenDB,
        tokenAccountBalance = diff.tokenAccountBalance,
        contractDB = diff.contractDB,
        contractTokens = diff.contractTokens,
        relatedAddress = diff.relatedAddress,
        chargedFee = tx.fee
      ))
      .left.flatMap( e =>
        Right(Diff(
          height = height,
          tx = tx,
          portfolios = Map(senderAddress -> Portfolio(-tx.fee, LeaseInfo.empty, Map.empty)),
          chargedFee = tx.fee,
          txStatus = e match {
            case ce: ContractValidationError  => ce.transactionStatus
            case _ => TransactionStatus.Failed
          }
        ))
      )
    })
  }
}
