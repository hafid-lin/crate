/*
 * Licensed to Crate under one or more contributor license agreements.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.  Crate licenses this file
 * to you under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial
 * agreement.
 */

package io.crate.protocols.postgres;

import io.crate.action.sql.ResultReceiver;
import io.crate.concurrent.CompletionListener;
import io.crate.concurrent.CompletionMultiListener;
import io.crate.core.collections.Row;
import io.crate.exceptions.Exceptions;
import io.crate.types.DataType;
import org.jboss.netty.channel.Channel;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

class ResultSetReceiver implements ResultReceiver {

    private final String query;
    private final Channel channel;
    private final List<? extends DataType> columnTypes;

    @Nullable
    private final FormatCodes.FormatCode[] formatCodes;

    private CompletionListener listener = CompletionListener.NO_OP;
    private long rowCount = 0;

    ResultSetReceiver(String query,
                      Channel channel,
                      List<? extends DataType> columnTypes,
                      @Nullable FormatCodes.FormatCode[] formatCodes) {
        this.query = query;
        this.channel = channel;
        this.columnTypes = columnTypes;
        this.formatCodes = formatCodes;
    }

    @Override
    public void setNextRow(Row row) {
        rowCount++;
        Messages.sendDataRow(channel, row, columnTypes, formatCodes);
    }

    @Override
    public void batchFinished() {
        Messages.sendPortalSuspended(channel);
        Messages.sendReadyForQuery(channel);
    }

    @Override
    public void allFinished() {
        Messages.sendCommandComplete(channel, query, rowCount);
        listener.onSuccess(null);
    }

    @Override
    public void fail(@Nonnull Throwable throwable) {
        Messages.sendErrorResponse(channel, Exceptions.messageOf(throwable));
        listener.onFailure(throwable);
    }

    @Override
    public void addListener(CompletionListener listener) {
        this.listener = CompletionMultiListener.merge(this.listener, listener);
    }
}