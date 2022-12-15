package com.cider.bot.config.listener;

import com.cider.bot.config.ApplicationProperties;
import com.cider.bot.config.ApplicationProperties.Cider;
import com.cider.bot.config.listener.event.GearBoughtEvent;
import com.cider.bot.config.listener.event.GearSoldEvent;
import com.cider.bot.config.listener.event.LPClaimedEvent;
import com.cider.bot.config.w3j.WebSocketClient;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.Utils;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.utils.Convert;
import org.web3j.utils.Convert.Unit;

@Component
@Slf4j
public class CiderContractListener {

  private final WebSocketClient webSocketClient;

  private final ApplicationEventPublisher applicationEventPublisher;

  private final ApplicationProperties applicationProperties;

  private EthFilterRun ethFilterRun;

  public CiderContractListener(
      WebSocketClient webSocketClient,
      ApplicationEventPublisher applicationEventPublisher,
      ApplicationProperties applicationProperties) {
    this.webSocketClient = webSocketClient;
    this.applicationEventPublisher = applicationEventPublisher;
    this.applicationProperties = applicationProperties;
  }

  @PostConstruct
  public void startEventFilter() {
    final Cider cider = applicationProperties.getCider();
    ethFilterRun =
        new EthFilterRun(
            webSocketClient.web3j(),
            cider.getContract(),
            cider.getGearSold(),
            cider.getGearBought(),
            cider.getLpClaimed(),
            applicationEventPublisher);
    Thread.ofVirtual().name("cider-log.start").start(ethFilterRun);
  }

  @PreDestroy
  public void destroy() {
    if (ethFilterRun != null) {
      try {
        ethFilterRun.stop();
      } catch (Exception ignored) {
      }
    }
  }

  public boolean checkEthFilterRunStatus() {
    if (ethFilterRun == null) {
      return false;
    }
    return ethFilterRun.isRunning();
  }

  public void restartEthFilterRun() {
    if (!checkEthFilterRunStatus()) {
      log.warn("startEthFilterRun Status is not Running!");
      return;
    }
    try {
      ethFilterRun.stop();
      ethFilterRun.start();
    } catch (Exception e) {
      log.error(" ethFilterRun Start error ", e);
      throw e;
    }
  }

  @Slf4j
  static class EthFilterRun implements Runnable, SmartLifecycle {

    private final Web3j web3j;

    private final String contract;

    private final String gearSold;

    private final String gearBought;

    private final String lpClaimed;

    private final ApplicationEventPublisher applicationEventPublisher;

    private Disposable disposable;

    EthFilterRun(
        Web3j web3j,
        String contract,
        String gearSold,
        String gearBought,
        String lpClaimed,
        ApplicationEventPublisher applicationEventPublisher) {
      this.web3j = web3j;
      this.contract = contract;
      this.gearSold = gearSold;
      this.gearBought = gearBought;
      this.lpClaimed = lpClaimed;
      this.applicationEventPublisher = applicationEventPublisher;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public void run() {
      log.info("Start LOG Filter");
      EthFilter filter =
          new EthFilter(
              DefaultBlockParameterName.LATEST, DefaultBlockParameterName.LATEST, contract);
      disposable =
          web3j
              .ethLogFlowable(filter)
              .subscribeOn(Schedulers.from(Executors.newVirtualThreadPerTaskExecutor()))
              .subscribe(
                  logs -> {
                    log.info("log:{}", logs);
                    final List<String> topics = logs.getTopics();
                    final String top = topics.get(0);
                    if (gearSold.equalsIgnoreCase(top)) {
                      final String addressRow = topics.get(1);
                      final String address = FunctionReturnDecoder.decodeAddress(addressRow);
                      final String logData = logs.getData();
                      List<TypeReference<?>> outputParameters;
                      outputParameters =
                          List.of(new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {});
                      final List<TypeReference<Type>> references = Utils.convert(outputParameters);
                      final List<Type> types = FunctionReturnDecoder.decode(logData, references);
                      final Type amountType = types.get(0);
                      final BigInteger amount = (BigInteger) amountType.getValue();
                      final Type shearedAmountType = types.get(1);
                      final BigInteger shearedAmount = (BigInteger) shearedAmountType.getValue();
                      final BigDecimal amountNumber =
                          Convert.fromWei(amount.toString(), Unit.ETHER);
                      final BigDecimal shearedAmountNumber =
                          Convert.fromWei(shearedAmount.toString(), Unit.ETHER);
                      log.info("Send GearSoldEvent  ");
                      applicationEventPublisher.publishEvent(
                          new GearSoldEvent(
                              logs.getTransactionHash(),
                              address,
                              amountNumber,
                              shearedAmountNumber));
                    } else if (gearBought.equalsIgnoreCase(top)) {
                      final String addressRow = topics.get(1);
                      final String address = FunctionReturnDecoder.decodeAddress(addressRow);
                      final String logData = logs.getData();
                      final List<TypeReference<?>> typeReferences =
                          List.of(new TypeReference<Uint256>() {});
                      final List<Type> types =
                          FunctionReturnDecoder.decode(logData, Utils.convert(typeReferences));
                      final Type amountType = types.get(0);
                      final BigInteger amount = (BigInteger) amountType.getValue();
                      final BigDecimal amountNumber =
                          Convert.fromWei(amount.toString(), Unit.ETHER);
                      log.info("Send GearBought Event ");
                      applicationEventPublisher.publishEvent(
                          new GearBoughtEvent(logs.getTransactionHash(), address, amountNumber));
                    } else if (lpClaimed.equalsIgnoreCase(top)) {
                      final String addressRow = topics.get(1);
                      final String contributor = FunctionReturnDecoder.decodeAddress(addressRow);
                      final String logData = logs.getData();
                      final List<TypeReference<?>> typeReferences =
                          List.of(new TypeReference<Uint256>() {}, new TypeReference<Uint256>() {});
                      final List<Type> types =
                          FunctionReturnDecoder.decode(logData, Utils.convert(typeReferences));
                      final Type amountType = types.get(0);
                      final BigInteger amount = (BigInteger) amountType.getValue();
                      final BigDecimal amountNumber =
                          Convert.fromWei(amount.toString(), Unit.ETHER);
                      log.info("Send LpClaimed Event");
                      applicationEventPublisher.publishEvent(
                          new LPClaimedEvent(logs.getTransactionHash(), contributor, amountNumber));
                    } else {
                      return;
                    }
                  },
                  throwable -> {
                    log.error("EthFilter Exception :", throwable);
                    log.warn("Start Filter");
                    start();
                  },
                  () -> {
                    log.warn("Start Filter");
                    start();
                  });
    }

    @Override
    public void start() {
      if (isRunning()) {
        log.error("LOG Listener Running!");
        return;
      }
      run();
      try {
        TimeUnit.MILLISECONDS.sleep(100);
      } catch (InterruptedException ignored) {
      }
      log.info("Eth LOG Listener Flowable Start!");
    }

    @Override
    public void stop() {
      if (disposable != null) {
        disposable.dispose();
        log.info("Eth LOG Listener Flowable complete！");
      } else {
        log.warn("Eth LOG Listener Flowable Not Running!");
      }
    }

    @Override
    public boolean isRunning() {
      return disposable != null && !disposable.isDisposed();
    }
  }
}
