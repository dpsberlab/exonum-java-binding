/*
 * Copyright 2018 The Exonum Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exonum.binding.cryptocurrency;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.exonum.binding.common.crypto.PublicKey;
import com.exonum.binding.common.hash.HashCode;
import com.exonum.binding.common.message.BinaryMessage;
import com.exonum.binding.cryptocurrency.transactions.CryptocurrencyTransactionGson;
import com.exonum.binding.cryptocurrency.transactions.CryptocurrencyTransactionTemplate;
import com.exonum.binding.service.InternalServerError;
import com.exonum.binding.transaction.Transaction;
import com.google.gson.reflect.TypeToken;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(VertxExtension.class)
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ApiControllerTest {

  private static final String HOST = "0.0.0.0";

  private static final short CREATE_WALLET_TX_ID = 1;

  private static final PublicKey fromKey = PredefinedOwnerKeys.firstOwnerKey;

  private CryptocurrencyService service;

  private ApiController controller;

  private HttpServer httpServer;

  private WebClient webClient;

  private volatile int port = -1;

  @BeforeEach
  void setup(Vertx vertx, VertxTestContext context) {
    service = mock(CryptocurrencyService.class);
    controller = new ApiController(service);

    httpServer = vertx.createHttpServer();
    webClient = WebClient.create(vertx);

    Router router = Router.router(vertx);
    controller.mountApi(router);

    httpServer.requestHandler(router::accept)
        .listen(0, context.succeeding(result -> {
          // Set the actual server port.
          port = result.actualPort();
          // Notify that the HTTP Server is accepting connections.
          context.completeNow();
        }));
  }

  @AfterEach
  void tearDown(VertxTestContext context) {
    webClient.close();
    httpServer.close((r) -> context.completeNow());
  }

  @Test
  void submitValidTransaction(VertxTestContext context) {
    BinaryMessage message = createTestBinaryMessage(CREATE_WALLET_TX_ID);

    String messageHash = "1234";
    Transaction transaction = mock(Transaction.class);

    when(service.convertToTransaction(any(BinaryMessage.class)))
        .thenReturn(transaction);
    when(service.submitTransaction(transaction))
        .thenReturn(HashCode.fromString(messageHash));

    String expectedResponse = messageHash;
    // Send a request to submitTransaction
    post(ApiController.SUBMIT_TRANSACTION_PATH)
        .sendBuffer(
            Buffer.buffer(message.getSignedMessage().array()),
            context.succeeding(
                response -> context.verify(() -> {
                  // Check the response status
                  int statusCode = response.statusCode();
                  assertEquals(HTTP_OK, statusCode);

                  // Check the payload type
                  String contentType = response.getHeader("Content-Type");
                  assertEquals("text/plain", contentType);

                  // Check the response body
                  String body = response.bodyAsString();
                  assertEquals(expectedResponse, body);

                  // Verify that a proper transaction was submitted to the network
                  verify(service).submitTransaction(transaction);

                  context.completeNow();
                })));
  }

  @Test
  void submitTransactionOfIncorrectMessageSize(VertxTestContext context) {
    BinaryMessage message = createTestBinaryMessage(CREATE_WALLET_TX_ID);
    byte errorByte = 1;

    post(ApiController.SUBMIT_TRANSACTION_PATH)
        .sendBuffer(
            Buffer.buffer(message.getSignedMessage().array()).appendByte(errorByte),
            context.succeeding(response -> context.verify(() -> {
              assertThat(response.statusCode()).isEqualTo(HTTP_BAD_REQUEST);

              verify(service, never()).convertToTransaction(any(BinaryMessage.class));
              verify(service, never()).submitTransaction(any(Transaction.class));

              context.completeNow();
            })));
  }

  @Test
  void submitTransactionWhenInternalServerErrorIsThrown(VertxTestContext context) {
    BinaryMessage message = createTestBinaryMessage(CREATE_WALLET_TX_ID);

    Transaction transaction = mock(Transaction.class);
    Throwable error = wrappingChecked(InternalServerError.class);

    when(service.convertToTransaction(any(BinaryMessage.class)))
        .thenReturn(transaction);
    when(service.submitTransaction(transaction))
        .thenThrow(error);

    post(ApiController.SUBMIT_TRANSACTION_PATH)
        .sendBuffer(
            Buffer.buffer(message.getSignedMessage().array()),
            context.succeeding(response -> context.verify(() -> {
              assertThat(response.statusCode()).isEqualTo(HTTP_INTERNAL_ERROR);
              verify(service).submitTransaction(transaction);

              context.completeNow();
            })));
  }

  @Test
  void getWallet(VertxTestContext context) {
    long balance = 200L;
    Wallet wallet = new Wallet(balance);
    when(service.getWallet(eq(fromKey)))
        .thenReturn(Optional.of(wallet));

    String getWalletUri = getWalletUri(fromKey);
    get(getWalletUri)
        .send(context.succeeding(response -> context.verify(() -> {
          assertThat(response.statusCode())
              .isEqualTo(HTTP_OK);

          String body = response.bodyAsString();
          Wallet actualWallet = CryptocurrencyTransactionGson.instance()
              .fromJson(body, Wallet.class);
          assertThat(actualWallet.getBalance()).isEqualTo(wallet.getBalance());

          context.completeNow();
        })));
  }

  @Test
  void getNonexistentWallet(VertxTestContext context) {
    when(service.getWallet(fromKey))
        .thenReturn(Optional.empty());

    String getWalletUri = getWalletUri(fromKey);
    get(getWalletUri)
        .send(context.succeeding(response -> context.verify(() -> {
          assertThat(response.statusCode()).isEqualTo(HTTP_NOT_FOUND);

          context.completeNow();
        })));
  }

  @Test
  void getWalletUsingInvalidKey(VertxTestContext context) {
    String publicKeyString = "Invalid key";
    String getWalletUri = getWalletUri(publicKeyString);

    get(getWalletUri)
        .send(context.succeeding(response -> context.verify(() -> {
          assertThat(response.statusCode()).isEqualTo(HTTP_BAD_REQUEST);
          assertThat(response.bodyAsString())
              .startsWith("Failed to convert parameter (walletId):");

          context.completeNow();
        })));
  }

  private BinaryMessage createTestBinaryMessage(short txId) {
    return CryptocurrencyTransactionTemplate.newCryptocurrencyTransactionBuilder(txId)
        .buildRaw();
  }

  @Test
  void getWalletHistory(VertxTestContext context) {
    List<HistoryEntity> history = singletonList(
        HistoryEntity.Builder.newBuilder()
            .setSeed(1L)
            .setWalletFrom(fromKey)
            .setWalletTo(fromKey)
            .setAmount(10L)
            .setTransactionHash(HashCode.fromString("a0a0a0"))
            .build()
    );
    when(service.getWalletHistory(fromKey)).thenReturn(history);

    String uri = getWalletUri(fromKey) + "/history";

    get(uri)
        .send(context.succeeding(response -> context.verify(() -> {
          assertThat(response.statusCode()).isEqualTo(HTTP_OK);

          List<HistoryEntity> actualHistory = parseWalletHistory(response);

          assertThat(actualHistory).isEqualTo(history);

          context.completeNow();
        })));
  }

  @Test
  void getWalletHistoryNonexistentWallet(VertxTestContext context) {
    when(service.getWalletHistory(fromKey)).thenReturn(emptyList());

    String uri = getWalletUri(fromKey) + "/history";

    get(uri)
        .send(context.succeeding(response -> context.verify(() -> {
          assertThat(response.statusCode()).isEqualTo(HTTP_OK);
          assertThat(parseWalletHistory(response)).isEmpty();
          context.completeNow();
        })));
  }

  private List<HistoryEntity> parseWalletHistory(HttpResponse<Buffer> response) {
    Type listType = new TypeToken<List<HistoryEntity>>() {
    }.getType();
    return CryptocurrencyTransactionGson.instance()
        .fromJson(response.bodyAsString(), listType);
  }

  private Throwable wrappingChecked(Class<? extends Throwable> checkedException) {
    Throwable wrappingException = logSafeExceptionMock(RuntimeException.class);
    Throwable cause = logSafeExceptionMock(checkedException);
    when(wrappingException.getCause()).thenReturn(cause);
    return wrappingException;
  }

  private Throwable logSafeExceptionMock(Class<? extends Throwable> exceptionType) {
    Throwable t = mock(exceptionType);
    return t;
  }

  private String getWalletUri(PublicKey publicKey) {
    return getWalletUri(publicKey.toString());
  }

  private String getWalletUri(String id) {
    try {
      return "/wallet/" + URLEncoder.encode(id, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      throw new AssertionError("UTF-8 must be supported", e);
    }
  }

  private HttpRequest<Buffer> get(String requestPath) {
    return webClient.get(port, HOST, requestPath);
  }

  private HttpRequest<Buffer> post(String requestPath) {
    return webClient.post(port, HOST, requestPath);
  }
}
