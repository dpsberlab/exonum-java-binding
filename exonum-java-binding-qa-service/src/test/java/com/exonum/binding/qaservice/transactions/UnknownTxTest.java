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

package com.exonum.binding.qaservice.transactions;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.exonum.binding.storage.database.Fork;
import org.junit.jupiter.api.Test;

class UnknownTxTest {

  @Test
  void isValid() {
    UnknownTx tx = new UnknownTx();

    assertTrue(tx.isValid());
  }

  @Test
  void execute() {
    UnknownTx tx = new UnknownTx();

    assertThrows(AssertionError.class,
        () -> tx.execute(mock(Fork.class)));
  }
}
