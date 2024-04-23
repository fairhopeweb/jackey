package io.jackey;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.jackey.Protocol.Command;
import io.jackey.Protocol.Keyword;
import io.jackey.args.BitCountOption;
import io.jackey.args.BitOP;
import io.jackey.args.ExpiryOption;
import io.jackey.args.FlushMode;
import io.jackey.args.FunctionRestorePolicy;
import io.jackey.args.GeoUnit;
import io.jackey.args.ListDirection;
import io.jackey.args.ListPosition;
import io.jackey.args.SortedSetOption;
import io.jackey.bloom.BFInsertParams;
import io.jackey.bloom.BFReserveParams;
import io.jackey.bloom.CFInsertParams;
import io.jackey.bloom.CFReserveParams;
import io.jackey.bloom.TDigestMergeParams;
import io.jackey.gears.TFunctionListParams;
import io.jackey.gears.TFunctionLoadParams;
import io.jackey.json.JsonBuilderFactory;
import io.jackey.json.JsonSetParams;
import io.jackey.json.Path;
import io.jackey.json.Path2;
import io.jackey.params.BitPosParams;
import io.jackey.params.GeoAddParams;
import io.jackey.params.GeoRadiusParam;
import io.jackey.params.GeoRadiusStoreParam;
import io.jackey.params.GeoSearchParam;
import io.jackey.params.GetExParams;
import io.jackey.params.LCSParams;
import io.jackey.params.LPosParams;
import io.jackey.params.MigrateParams;
import io.jackey.params.RestoreParams;
import io.jackey.params.ScanParams;
import io.jackey.params.SetParams;
import io.jackey.params.SortingParams;
import io.jackey.params.XAddParams;
import io.jackey.params.XAutoClaimParams;
import io.jackey.params.XClaimParams;
import io.jackey.params.XPendingParams;
import io.jackey.params.XReadGroupParams;
import io.jackey.params.XReadParams;
import io.jackey.params.XTrimParams;
import io.jackey.params.ZAddParams;
import io.jackey.params.ZIncrByParams;
import io.jackey.params.ZParams;
import io.jackey.params.ZRangeParams;
import io.jackey.resps.FunctionStats;
import io.jackey.resps.GeoRadiusResponse;
import io.jackey.resps.LCSMatchResult;
import io.jackey.resps.LibraryInfo;
import io.jackey.resps.ScanResult;
import io.jackey.resps.StreamConsumerInfo;
import io.jackey.resps.StreamConsumersInfo;
import io.jackey.resps.StreamEntry;
import io.jackey.resps.StreamFullInfo;
import io.jackey.resps.StreamGroupInfo;
import io.jackey.resps.StreamInfo;
import io.jackey.resps.StreamPendingEntry;
import io.jackey.resps.StreamPendingSummary;
import io.jackey.resps.Tuple;
import io.jackey.search.FTCreateParams;
import io.jackey.search.FTProfileParams;
import io.jackey.search.FTSearchParams;
import io.jackey.search.FTSpellCheckParams;
import io.jackey.search.IndexOptions;
import io.jackey.search.Query;
import io.jackey.search.Schema;
import io.jackey.search.SearchBuilderFactory;
import io.jackey.search.SearchResult;
import io.jackey.timeseries.AggregationType;
import io.jackey.timeseries.TSAlterParams;
import io.jackey.timeseries.TSCreateParams;
import io.jackey.timeseries.TSElement;
import io.jackey.timeseries.TSGetParams;
import io.jackey.timeseries.TSInfo;
import io.jackey.timeseries.TSMGetElement;
import io.jackey.timeseries.TSMGetParams;
import io.jackey.timeseries.TSMRangeElements;
import io.jackey.timeseries.TSMRangeParams;
import io.jackey.timeseries.TSRangeParams;
import io.jackey.timeseries.TimeSeriesBuilderFactory;
import org.json.JSONArray;
import org.json.JSONObject;

import io.jackey.args.*;
import io.jackey.bloom.*;
import io.jackey.bloom.RedisBloomProtocol.*;
import io.jackey.commands.ProtocolCommand;
import io.jackey.gears.*;
import io.jackey.gears.RedisGearsProtocol.*;
import io.jackey.gears.resps.GearsLibraryInfo;
import io.jackey.graph.GraphProtocol.*;
import io.jackey.json.*;
import io.jackey.json.JsonProtocol.JsonCommand;
import io.jackey.json.DefaultGsonObjectMapper;
import io.jackey.json.JsonObjectMapper;
import io.jackey.params.*;
import io.jackey.resps.*;
import io.jackey.search.*;
import io.jackey.search.SearchProtocol.*;
import io.jackey.search.SearchResult.SearchResultBuilder;
import io.jackey.search.aggr.AggregationBuilder;
import io.jackey.search.aggr.AggregationResult;
import io.jackey.search.schemafields.SchemaField;
import io.jackey.timeseries.*;
import io.jackey.timeseries.TimeSeriesProtocol.*;
import io.jackey.util.KeyValue;

public class CommandObjects {

  private RedisProtocol protocol;

  // TODO: restrict?
  public final void setProtocol(RedisProtocol proto) {
    this.protocol = proto;
  }

  // TODO: remove?
  protected RedisProtocol getProtocol() {
    return protocol;
  }

  private volatile JsonObjectMapper jsonObjectMapper;
  private final AtomicInteger searchDialect = new AtomicInteger(0);

  private JedisBroadcastAndRoundRobinConfig broadcastAndRoundRobinConfig = null;

  void setBroadcastAndRoundRobinConfig(JedisBroadcastAndRoundRobinConfig config) {
    this.broadcastAndRoundRobinConfig = config;
  }

  protected CommandArguments commandArguments(ProtocolCommand command) {
    return new CommandArguments(command);
  }

  private final CommandObject<String> PING_COMMAND_OBJECT = new CommandObject<>(commandArguments(Command.PING), BuilderFactory.STRING);

  public final CommandObject<String> ping() {
    return PING_COMMAND_OBJECT;
  }

  private final CommandObject<String> FLUSHALL_COMMAND_OBJECT = new CommandObject<>(commandArguments(Command.FLUSHALL), BuilderFactory.STRING);

  public final CommandObject<String> flushAll() {
    return FLUSHALL_COMMAND_OBJECT;
  }

  private final CommandObject<String> FLUSHDB_COMMAND_OBJECT = new CommandObject<>(commandArguments(Command.FLUSHDB), BuilderFactory.STRING);

  public final CommandObject<String> flushDB() {
    return FLUSHDB_COMMAND_OBJECT;
  }

  public final CommandObject<String> configSet(String parameter, String value) {
    return new CommandObject<>(commandArguments(Command.CONFIG).add(Keyword.SET).add(parameter).add(value), BuilderFactory.STRING);
  }

  // Key commands
  public final CommandObject<Boolean> exists(String key) {
    return new CommandObject<>(commandArguments(Command.EXISTS).key(key), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Long> exists(String... keys) {
    return new CommandObject<>(commandArguments(Command.EXISTS).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Boolean> exists(byte[] key) {
    return new CommandObject<>(commandArguments(Command.EXISTS).key(key), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Long> exists(byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.EXISTS).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Long> persist(String key) {
    return new CommandObject<>(commandArguments(Command.PERSIST).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> persist(byte[] key) {
    return new CommandObject<>(commandArguments(Command.PERSIST).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<String> type(String key) {
    return new CommandObject<>(commandArguments(Command.TYPE).key(key), BuilderFactory.STRING);
  }

  public final CommandObject<String> type(byte[] key) {
    return new CommandObject<>(commandArguments(Command.TYPE).key(key), BuilderFactory.STRING);
  }

  public final CommandObject<byte[]> dump(String key) {
    return new CommandObject<>(commandArguments(Command.DUMP).key(key), BuilderFactory.BINARY);
  }

  public final CommandObject<byte[]> dump(byte[] key) {
    return new CommandObject<>(commandArguments(Command.DUMP).key(key), BuilderFactory.BINARY);
  }

  public final CommandObject<String> restore(String key, long ttl, byte[] serializedValue) {
    return new CommandObject<>(commandArguments(Command.RESTORE).key(key).add(ttl)
        .add(serializedValue), BuilderFactory.STRING);
  }

  public final CommandObject<String> restore(String key, long ttl, byte[] serializedValue, RestoreParams params) {
    return new CommandObject<>(commandArguments(Command.RESTORE).key(key).add(ttl)
        .add(serializedValue).addParams(params), BuilderFactory.STRING);
  }

  public final CommandObject<String> restore(byte[] key, long ttl, byte[] serializedValue) {
    return new CommandObject<>(commandArguments(Command.RESTORE).key(key).add(ttl)
        .add(serializedValue), BuilderFactory.STRING);
  }

  public final CommandObject<String> restore(byte[] key, long ttl, byte[] serializedValue, RestoreParams params) {
    return new CommandObject<>(commandArguments(Command.RESTORE).key(key).add(ttl)
        .add(serializedValue).addParams(params), BuilderFactory.STRING);
  }

  public final CommandObject<Long> expire(String key, long seconds) {
    return new CommandObject<>(commandArguments(Command.EXPIRE).key(key).add(seconds), BuilderFactory.LONG);
  }

  public final CommandObject<Long> expire(byte[] key, long seconds) {
    return new CommandObject<>(commandArguments(Command.EXPIRE).key(key).add(seconds), BuilderFactory.LONG);
  }

  public final CommandObject<Long> expire(String key, long seconds, ExpiryOption expiryOption) {
    return new CommandObject<>(commandArguments(Command.EXPIRE).key(key).add(seconds).add(expiryOption),
        BuilderFactory.LONG);
  }

  public final CommandObject<Long> expire(byte[] key, long seconds, ExpiryOption expiryOption) {
    return new CommandObject<>(commandArguments(Command.EXPIRE).key(key).add(seconds).add(expiryOption),
        BuilderFactory.LONG);
  }

  public final CommandObject<Long> pexpire(String key, long milliseconds) {
    return new CommandObject<>(commandArguments(Command.PEXPIRE).key(key).add(milliseconds), BuilderFactory.LONG);
  }

  public final CommandObject<Long> pexpire(byte[] key, long milliseconds) {
    return new CommandObject<>(commandArguments(Command.PEXPIRE).key(key).add(milliseconds), BuilderFactory.LONG);
  }

  public final CommandObject<Long> pexpire(String key, long milliseconds, ExpiryOption expiryOption) {
    return new CommandObject<>(commandArguments(Command.PEXPIRE).key(key).add(milliseconds).add(expiryOption),
        BuilderFactory.LONG);
  }

  public final CommandObject<Long> pexpire(byte[] key, long milliseconds, ExpiryOption expiryOption) {
    return new CommandObject<>(commandArguments(Command.PEXPIRE).key(key).add(milliseconds).add(expiryOption),
        BuilderFactory.LONG);
  }

  public final CommandObject<Long> expireTime(String key) {
    return new CommandObject<>(commandArguments(Command.EXPIRETIME).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> expireTime(byte[] key) {
    return new CommandObject<>(commandArguments(Command.EXPIRETIME).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> pexpireTime(String key) {
    return new CommandObject<>(commandArguments(Command.PEXPIRETIME).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> pexpireTime(byte[] key) {
    return new CommandObject<>(commandArguments(Command.PEXPIRETIME).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> expireAt(String key, long unixTime) {
    return new CommandObject<>(commandArguments(Command.EXPIREAT).key(key).add(unixTime), BuilderFactory.LONG);
  }

  public final CommandObject<Long> expireAt(byte[] key, long unixTime) {
    return new CommandObject<>(commandArguments(Command.EXPIREAT).key(key).add(unixTime), BuilderFactory.LONG);
  }

  public final CommandObject<Long> expireAt(String key, long unixTime, ExpiryOption expiryOption) {
    return new CommandObject<>(commandArguments(Command.EXPIREAT).key(key).add(unixTime).add(expiryOption), BuilderFactory.LONG);
  }

  public final CommandObject<Long> expireAt(byte[] key, long unixTime, ExpiryOption expiryOption) {
    return new CommandObject<>(commandArguments(Command.EXPIREAT).key(key).add(unixTime).add(expiryOption), BuilderFactory.LONG);
  }

  public final CommandObject<Long> pexpireAt(String key, long millisecondsTimestamp) {
    return new CommandObject<>(commandArguments(Command.PEXPIREAT).key(key).add(millisecondsTimestamp), BuilderFactory.LONG);
  }

  public final CommandObject<Long> pexpireAt(byte[] key, long millisecondsTimestamp) {
    return new CommandObject<>(commandArguments(Command.PEXPIREAT).key(key).add(millisecondsTimestamp), BuilderFactory.LONG);
  }

  public final CommandObject<Long> pexpireAt(String key, long millisecondsTimestamp, ExpiryOption expiryOption) {
    return new CommandObject<>(commandArguments(Command.PEXPIREAT).key(key).add(millisecondsTimestamp).add(expiryOption),
        BuilderFactory.LONG);
  }

  public final CommandObject<Long> pexpireAt(byte[] key, long millisecondsTimestamp, ExpiryOption expiryOption) {
    return new CommandObject<>(commandArguments(Command.PEXPIREAT).key(key).add(millisecondsTimestamp).add(expiryOption),
        BuilderFactory.LONG);
  }

  public final CommandObject<Long> ttl(String key) {
    return new CommandObject<>(commandArguments(Command.TTL).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> ttl(byte[] key) {
    return new CommandObject<>(commandArguments(Command.TTL).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> pttl(String key) {
    return new CommandObject<>(commandArguments(Command.PTTL).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> pttl(byte[] key) {
    return new CommandObject<>(commandArguments(Command.PTTL).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> touch(String key) {
    return new CommandObject<>(commandArguments(Command.TOUCH).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> touch(String... keys) {
    return new CommandObject<>(commandArguments(Command.TOUCH).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Long> touch(byte[] key) {
    return new CommandObject<>(commandArguments(Command.TOUCH).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> touch(byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.TOUCH).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<List<String>> sort(String key) {
    return new CommandObject<>(commandArguments(Command.SORT).key(key), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> sort(String key, SortingParams sortingParams) {
    return new CommandObject<>(commandArguments(Command.SORT).key(key).addParams(sortingParams), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<byte[]>> sort(byte[] key) {
    return new CommandObject<>(commandArguments(Command.SORT).key(key), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<byte[]>> sort(byte[] key, SortingParams sortingParams) {
    return new CommandObject<>(commandArguments(Command.SORT).key(key).addParams(sortingParams), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<Long> sort(String key, String dstkey) {
    return new CommandObject<>(commandArguments(Command.SORT).key(key)
        .add(Keyword.STORE).key(dstkey), BuilderFactory.LONG);
  }

  public final CommandObject<Long> sort(String key, SortingParams sortingParams, String dstkey) {
    return new CommandObject<>(commandArguments(Command.SORT).key(key).addParams(sortingParams)
        .add(Keyword.STORE).key(dstkey), BuilderFactory.LONG);
  }

  public final CommandObject<Long> sort(byte[] key, byte[] dstkey) {
    return new CommandObject<>(commandArguments(Command.SORT).key(key)
        .add(Keyword.STORE).key(dstkey), BuilderFactory.LONG);
  }

  public final CommandObject<Long> sort(byte[] key, SortingParams sortingParams, byte[] dstkey) {
    return new CommandObject<>(commandArguments(Command.SORT).key(key).addParams(sortingParams)
        .add(Keyword.STORE).key(dstkey), BuilderFactory.LONG);
  }

  public final CommandObject<List<byte[]>> sortReadonly(byte[] key, SortingParams sortingParams) {
    return new CommandObject<>(commandArguments(Command.SORT_RO).key(key).addParams(sortingParams),
        BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<String>> sortReadonly(String key, SortingParams sortingParams) {
    return new CommandObject<>(commandArguments(Command.SORT_RO).key(key).addParams(sortingParams),
        BuilderFactory.STRING_LIST);
  }

  public final CommandObject<Long> del(String key) {
    return new CommandObject<>(commandArguments(Command.DEL).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> del(String... keys) {
    return new CommandObject<>(commandArguments(Command.DEL).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Long> del(byte[] key) {
    return new CommandObject<>(commandArguments(Command.DEL).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> del(byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.DEL).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Long> unlink(String key) {
    return new CommandObject<>(commandArguments(Command.UNLINK).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> unlink(String... keys) {
    return new CommandObject<>(commandArguments(Command.UNLINK).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Long> unlink(byte[] key) {
    return new CommandObject<>(commandArguments(Command.UNLINK).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> unlink(byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.UNLINK).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Boolean> copy(String srcKey, String dstKey, boolean replace) {
    CommandArguments args = commandArguments(Command.COPY).key(srcKey).key(dstKey);
    if (replace) {
      args.add(Keyword.REPLACE);
    }
    return new CommandObject<>(args, BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Boolean> copy(byte[] srcKey, byte[] dstKey, boolean replace) {
    CommandArguments args = commandArguments(Command.COPY).key(srcKey).key(dstKey);
    if (replace) {
      args.add(Keyword.REPLACE);
    }
    return new CommandObject<>(args, BuilderFactory.BOOLEAN);
  }

  public final CommandObject<String> rename(String oldkey, String newkey) {
    return new CommandObject<>(commandArguments(Command.RENAME).key(oldkey).key(newkey), BuilderFactory.STRING);
  }

  public final CommandObject<Long> renamenx(String oldkey, String newkey) {
    return new CommandObject<>(commandArguments(Command.RENAMENX).key(oldkey).key(newkey), BuilderFactory.LONG);
  }

  public final CommandObject<String> rename(byte[] oldkey, byte[] newkey) {
    return new CommandObject<>(commandArguments(Command.RENAME).key(oldkey).key(newkey), BuilderFactory.STRING);
  }

  public final CommandObject<Long> renamenx(byte[] oldkey, byte[] newkey) {
    return new CommandObject<>(commandArguments(Command.RENAMENX).key(oldkey).key(newkey), BuilderFactory.LONG);
  }

  public CommandObject<Long> dbSize() {
    return new CommandObject<>(commandArguments(Command.DBSIZE), BuilderFactory.LONG);
  }

  public CommandObject<Set<String>> keys(String pattern) {
    CommandArguments args = commandArguments(Command.KEYS).key(pattern);
    return new CommandObject<>(args, BuilderFactory.STRING_SET);
  }

  public CommandObject<Set<byte[]>> keys(byte[] pattern) {
    CommandArguments args = commandArguments(Command.KEYS).key(pattern);
    return new CommandObject<>(args, BuilderFactory.BINARY_SET);
  }

  public CommandObject<ScanResult<String>> scan(String cursor) {
    return new CommandObject<>(commandArguments(Command.SCAN).add(cursor), BuilderFactory.SCAN_RESPONSE);
  }

  public CommandObject<ScanResult<String>> scan(String cursor, ScanParams params) {
    return new CommandObject<>(commandArguments(Command.SCAN).add(cursor).addParams(params), BuilderFactory.SCAN_RESPONSE);
  }

  public CommandObject<ScanResult<String>> scan(String cursor, ScanParams params, String type) {
    return new CommandObject<>(commandArguments(Command.SCAN).add(cursor).addParams(params).add(Keyword.TYPE).add(type), BuilderFactory.SCAN_RESPONSE);
  }

  public CommandObject<ScanResult<byte[]>> scan(byte[] cursor) {
    return new CommandObject<>(commandArguments(Command.SCAN).add(cursor), BuilderFactory.SCAN_BINARY_RESPONSE);
  }

  public CommandObject<ScanResult<byte[]>> scan(byte[] cursor, ScanParams params) {
    return new CommandObject<>(commandArguments(Command.SCAN).add(cursor).addParams(params), BuilderFactory.SCAN_BINARY_RESPONSE);
  }

  public CommandObject<ScanResult<byte[]>> scan(byte[] cursor, ScanParams params, byte[] type) {
    return new CommandObject<>(commandArguments(Command.SCAN).add(cursor).addParams(params).add(Keyword.TYPE).add(type), BuilderFactory.SCAN_BINARY_RESPONSE);
  }

  public final CommandObject<String> randomKey() {
    return new CommandObject<>(commandArguments(Command.RANDOMKEY), BuilderFactory.STRING);
  }

  public final CommandObject<byte[]> randomBinaryKey() {
    return new CommandObject<>(commandArguments(Command.RANDOMKEY), BuilderFactory.BINARY);
  }
  // Key commands

  // String commands
  public final CommandObject<String> set(String key, String value) {
    return new CommandObject<>(commandArguments(Command.SET).key(key).add(value), BuilderFactory.STRING);
  }

  public final CommandObject<String> set(String key, String value, SetParams params) {
    return new CommandObject<>(commandArguments(Command.SET).key(key).add(value).addParams(params), BuilderFactory.STRING);
  }

  public final CommandObject<String> set(byte[] key, byte[] value) {
    return new CommandObject<>(commandArguments(Command.SET).key(key).add(value), BuilderFactory.STRING);
  }

  public final CommandObject<String> set(byte[] key, byte[] value, SetParams params) {
    return new CommandObject<>(commandArguments(Command.SET).key(key).add(value).addParams(params), BuilderFactory.STRING);
  }

  public final CommandObject<String> get(String key) {
    return new CommandObject<>(commandArguments(Command.GET).key(key), BuilderFactory.STRING);
  }

  public final CommandObject<String> setGet(String key, String value) {
    return new CommandObject<>(commandArguments(Command.SET).key(key).add(value).add(Keyword.GET), BuilderFactory.STRING);
  }

  public final CommandObject<String> setGet(String key, String value, SetParams params) {
    return new CommandObject<>(commandArguments(Command.SET).key(key).add(value).addParams(params).add(Keyword.GET),
        BuilderFactory.STRING);
  }

  public final CommandObject<String> getDel(String key) {
    return new CommandObject<>(commandArguments(Command.GETDEL).key(key), BuilderFactory.STRING);
  }

  public final CommandObject<String> getEx(String key, GetExParams params) {
    return new CommandObject<>(commandArguments(Command.GETEX).key(key).addParams(params), BuilderFactory.STRING);
  }

  public final CommandObject<byte[]> get(byte[] key) {
    return new CommandObject<>(commandArguments(Command.GET).key(key), BuilderFactory.BINARY);
  }

  public final CommandObject<byte[]> setGet(byte[] key, byte[] value) {
    return new CommandObject<>(commandArguments(Command.SET).key(key).add(value).add(Keyword.GET), BuilderFactory.BINARY);
  }

  public final CommandObject<byte[]> setGet(byte[] key, byte[] value, SetParams params) {
    return new CommandObject<>(commandArguments(Command.SET).key(key).add(value).addParams(params).add(Keyword.GET),
        BuilderFactory.BINARY);
  }

  public final CommandObject<byte[]> getDel(byte[] key) {
    return new CommandObject<>(commandArguments(Command.GETDEL).key(key), BuilderFactory.BINARY);
  }

  public final CommandObject<byte[]> getEx(byte[] key, GetExParams params) {
    return new CommandObject<>(commandArguments(Command.GETEX).key(key).addParams(params), BuilderFactory.BINARY);
  }

  /**
   * @deprecated Use {@link CommandObjects#setGet(java.lang.String, java.lang.String)}.
   */
  @Deprecated
  public final CommandObject<String> getSet(String key, String value) {
    return new CommandObject<>(commandArguments(Command.GETSET).key(key).add(value), BuilderFactory.STRING);
  }

  /**
   * @deprecated Use {@link CommandObjects#setGet(byte[], byte[])}.
   */
  @Deprecated
  public final CommandObject<byte[]> getSet(byte[] key, byte[] value) {
    return new CommandObject<>(commandArguments(Command.GETSET).key(key).add(value), BuilderFactory.BINARY);
  }

  public final CommandObject<Long> setnx(String key, String value) {
    return new CommandObject<>(commandArguments(Command.SETNX).key(key).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<String> setex(String key, long seconds, String value) {
    return new CommandObject<>(commandArguments(Command.SETEX).key(key).add(seconds).add(value), BuilderFactory.STRING);
  }

  public final CommandObject<String> psetex(String key, long milliseconds, String value) {
    return new CommandObject<>(commandArguments(Command.PSETEX).key(key).add(milliseconds).add(value), BuilderFactory.STRING);
  }

  public final CommandObject<Long> setnx(byte[] key, byte[] value) {
    return new CommandObject<>(commandArguments(Command.SETNX).key(key).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<String> setex(byte[] key, long seconds, byte[] value) {
    return new CommandObject<>(commandArguments(Command.SETEX).key(key).add(seconds).add(value), BuilderFactory.STRING);
  }

  public final CommandObject<String> psetex(byte[] key, long milliseconds, byte[] value) {
    return new CommandObject<>(commandArguments(Command.PSETEX).key(key).add(milliseconds).add(value), BuilderFactory.STRING);
  }

  public final CommandObject<Boolean> setbit(String key, long offset, boolean value) {
    return new CommandObject<>(commandArguments(Command.SETBIT).key(key).add(offset).add(value), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Boolean> setbit(byte[] key, long offset, boolean value) {
    return new CommandObject<>(commandArguments(Command.SETBIT).key(key).add(offset).add(value), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Boolean> getbit(String key, long offset) {
    return new CommandObject<>(commandArguments(Command.GETBIT).key(key).add(offset), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Boolean> getbit(byte[] key, long offset) {
    return new CommandObject<>(commandArguments(Command.GETBIT).key(key).add(offset), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Long> setrange(String key, long offset, String value) {
    return new CommandObject<>(commandArguments(Command.SETRANGE).key(key).add(offset).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<Long> setrange(byte[] key, long offset, byte[] value) {
    return new CommandObject<>(commandArguments(Command.SETRANGE).key(key).add(offset).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<String> getrange(String key, long startOffset, long endOffset) {
    return new CommandObject<>(commandArguments(Command.GETRANGE).key(key).add(startOffset).add(endOffset), BuilderFactory.STRING);
  }

  public final CommandObject<byte[]> getrange(byte[] key, long startOffset, long endOffset) {
    return new CommandObject<>(commandArguments(Command.GETRANGE).key(key).add(startOffset).add(endOffset), BuilderFactory.BINARY);
  }

  public final CommandObject<List<String>> mget(String... keys) {
    return new CommandObject<>(commandArguments(Command.MGET).keys((Object[]) keys), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<byte[]>> mget(byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.MGET).keys((Object[]) keys), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<String> mset(String... keysvalues) {
    return new CommandObject<>(addFlatKeyValueArgs(commandArguments(Command.MSET), keysvalues), BuilderFactory.STRING);
  }

  public final CommandObject<Long> msetnx(String... keysvalues) {
    return new CommandObject<>(addFlatKeyValueArgs(commandArguments(Command.MSETNX), keysvalues), BuilderFactory.LONG);
  }

  public final CommandObject<String> mset(byte[]... keysvalues) {
    return new CommandObject<>(addFlatKeyValueArgs(commandArguments(Command.MSET), keysvalues), BuilderFactory.STRING);
  }

  public final CommandObject<Long> msetnx(byte[]... keysvalues) {
    return new CommandObject<>(addFlatKeyValueArgs(commandArguments(Command.MSETNX), keysvalues), BuilderFactory.LONG);
  }

  public final CommandObject<Long> incr(String key) {
    return new CommandObject<>(commandArguments(Command.INCR).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> incrBy(String key, long increment) {
    return new CommandObject<>(commandArguments(Command.INCRBY).key(key).add(increment), BuilderFactory.LONG);
  }

  public final CommandObject<Double> incrByFloat(String key, double increment) {
    return new CommandObject<>(commandArguments(Command.INCRBYFLOAT).key(key).add(increment), BuilderFactory.DOUBLE);
  }

  public final CommandObject<Long> incr(byte[] key) {
    return new CommandObject<>(commandArguments(Command.INCR).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> incrBy(byte[] key, long increment) {
    return new CommandObject<>(commandArguments(Command.INCRBY).key(key).add(increment), BuilderFactory.LONG);
  }

  public final CommandObject<Double> incrByFloat(byte[] key, double increment) {
    return new CommandObject<>(commandArguments(Command.INCRBYFLOAT).key(key).add(increment), BuilderFactory.DOUBLE);
  }

  public final CommandObject<Long> decr(String key) {
    return new CommandObject<>(commandArguments(Command.DECR).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> decrBy(String key, long decrement) {
    return new CommandObject<>(commandArguments(Command.DECRBY).key(key).add(decrement), BuilderFactory.LONG);
  }

  public final CommandObject<Long> decr(byte[] key) {
    return new CommandObject<>(commandArguments(Command.DECR).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> decrBy(byte[] key, long decrement) {
    return new CommandObject<>(commandArguments(Command.DECRBY).key(key).add(decrement), BuilderFactory.LONG);
  }

  public final CommandObject<Long> append(String key, String value) {
    return new CommandObject<>(commandArguments(Command.APPEND).key(key).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<Long> append(byte[] key, byte[] value) {
    return new CommandObject<>(commandArguments(Command.APPEND).key(key).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<String> substr(String key, int start, int end) {
    return new CommandObject<>(commandArguments(Command.SUBSTR).key(key).add(start).add(end), BuilderFactory.STRING);
  }

  public final CommandObject<byte[]> substr(byte[] key, int start, int end) {
    return new CommandObject<>(commandArguments(Command.SUBSTR).key(key).add(start).add(end), BuilderFactory.BINARY);
  }

  public final CommandObject<Long> strlen(String key) {
    return new CommandObject<>(commandArguments(Command.STRLEN).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> strlen(byte[] key) {
    return new CommandObject<>(commandArguments(Command.STRLEN).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> bitcount(String key) {
    return new CommandObject<>(commandArguments(Command.BITCOUNT).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> bitcount(String key, long start, long end) {
    return new CommandObject<>(commandArguments(Command.BITCOUNT).key(key).add(start).add(end), BuilderFactory.LONG);
  }

  public final CommandObject<Long> bitcount(String key, long start, long end, BitCountOption option) {
    return new CommandObject<>(commandArguments(Command.BITCOUNT).key(key).add(start).add(end).add(option), BuilderFactory.LONG);
  }

  public final CommandObject<Long> bitcount(byte[] key) {
    return new CommandObject<>(commandArguments(Command.BITCOUNT).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> bitcount(byte[] key, long start, long end) {
    return new CommandObject<>(commandArguments(Command.BITCOUNT).key(key).add(start).add(end), BuilderFactory.LONG);
  }

  public final CommandObject<Long> bitcount(byte[] key, long start, long end, BitCountOption option) {
    return new CommandObject<>(commandArguments(Command.BITCOUNT).key(key).add(start).add(end).add(option), BuilderFactory.LONG);
  }

  public final CommandObject<Long> bitpos(String key, boolean value) {
    return new CommandObject<>(commandArguments(Command.BITPOS).key(key).add(value ? 1 : 0), BuilderFactory.LONG);
  }

  public final CommandObject<Long> bitpos(String key, boolean value, BitPosParams params) {
    return new CommandObject<>(commandArguments(Command.BITPOS).key(key).add(value ? 1 : 0).addParams(params), BuilderFactory.LONG);
  }

  public final CommandObject<Long> bitpos(byte[] key, boolean value) {
    return new CommandObject<>(commandArguments(Command.BITPOS).key(key).add(value ? 1 : 0), BuilderFactory.LONG);
  }

  public final CommandObject<Long> bitpos(byte[] key, boolean value, BitPosParams params) {
    return new CommandObject<>(commandArguments(Command.BITPOS).key(key).add(value ? 1 : 0).addParams(params), BuilderFactory.LONG);
  }

  public final CommandObject<List<Long>> bitfield(String key, String... arguments) {
    return new CommandObject<>(commandArguments(Command.BITFIELD).key(key).addObjects((Object[]) arguments), BuilderFactory.LONG_LIST);
  }

  public final CommandObject<List<Long>> bitfieldReadonly(String key, String... arguments) {
    return new CommandObject<>(commandArguments(Command.BITFIELD_RO).key(key).addObjects((Object[]) arguments), BuilderFactory.LONG_LIST);
  }

  public final CommandObject<List<Long>> bitfield(byte[] key, byte[]... arguments) {
    return new CommandObject<>(commandArguments(Command.BITFIELD).key(key).addObjects((Object[]) arguments), BuilderFactory.LONG_LIST);
  }

  public final CommandObject<List<Long>> bitfieldReadonly(byte[] key, byte[]... arguments) {
    return new CommandObject<>(commandArguments(Command.BITFIELD_RO).key(key).addObjects((Object[]) arguments), BuilderFactory.LONG_LIST);
  }

  public final CommandObject<Long> bitop(BitOP op, String destKey, String... srcKeys) {
    return new CommandObject<>(commandArguments(Command.BITOP).add(op).key(destKey).keys((Object[]) srcKeys), BuilderFactory.LONG);
  }

  public final CommandObject<Long> bitop(BitOP op, byte[] destKey, byte[]... srcKeys) {
    return new CommandObject<>(commandArguments(Command.BITOP).add(op).key(destKey).keys((Object[]) srcKeys), BuilderFactory.LONG);
  }

  public final CommandObject<LCSMatchResult> lcs(String keyA, String keyB, LCSParams params) {
    return new CommandObject<>(commandArguments(Command.LCS).key(keyA).key(keyB)
        .addParams(params), BuilderFactory.STR_ALGO_LCS_RESULT_BUILDER);
  }

  public final CommandObject<LCSMatchResult> lcs(byte[] keyA, byte[] keyB, LCSParams params) {
    return new CommandObject<>(commandArguments(Command.LCS).key(keyA).key(keyB)
        .addParams(params), BuilderFactory.STR_ALGO_LCS_RESULT_BUILDER);
  }
  // String commands

  // List commands
  public final CommandObject<Long> rpush(String key, String... strings) {
    return new CommandObject<>(commandArguments(Command.RPUSH).key(key).addObjects((Object[]) strings), BuilderFactory.LONG);
  }

  public final CommandObject<Long> rpush(byte[] key, byte[]... strings) {
    return new CommandObject<>(commandArguments(Command.RPUSH).key(key).addObjects((Object[]) strings), BuilderFactory.LONG);
  }

  public final CommandObject<Long> lpush(String key, String... strings) {
    return new CommandObject<>(commandArguments(Command.LPUSH).key(key).addObjects((Object[]) strings), BuilderFactory.LONG);
  }

  public final CommandObject<Long> lpush(byte[] key, byte[]... strings) {
    return new CommandObject<>(commandArguments(Command.LPUSH).key(key).addObjects((Object[]) strings), BuilderFactory.LONG);
  }

  public final CommandObject<Long> llen(String key) {
    return new CommandObject<>(commandArguments(Command.LLEN).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> llen(byte[] key) {
    return new CommandObject<>(commandArguments(Command.LLEN).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<List<String>> lrange(String key, long start, long stop) {
    return new CommandObject<>(commandArguments(Command.LRANGE).key(key).add(start).add(stop), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<byte[]>> lrange(byte[] key, long start, long stop) {
    return new CommandObject<>(commandArguments(Command.LRANGE).key(key).add(start).add(stop), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<String> ltrim(String key, long start, long stop) {
    return new CommandObject<>(commandArguments(Command.LTRIM).key(key).add(start).add(stop), BuilderFactory.STRING);
  }

  public final CommandObject<String> ltrim(byte[] key, long start, long stop) {
    return new CommandObject<>(commandArguments(Command.LTRIM).key(key).add(start).add(stop), BuilderFactory.STRING);
  }

  public final CommandObject<String> lindex(String key, long index) {
    return new CommandObject<>(commandArguments(Command.LINDEX).key(key).add(index), BuilderFactory.STRING);
  }

  public final CommandObject<byte[]> lindex(byte[] key, long index) {
    return new CommandObject<>(commandArguments(Command.LINDEX).key(key).add(index), BuilderFactory.BINARY);
  }

  public final CommandObject<String> lset(String key, long index, String value) {
    return new CommandObject<>(commandArguments(Command.LSET).key(key).add(index).add(value), BuilderFactory.STRING);
  }

  public final CommandObject<String> lset(byte[] key, long index, byte[] value) {
    return new CommandObject<>(commandArguments(Command.LSET).key(key).add(index).add(value), BuilderFactory.STRING);
  }

  public final CommandObject<Long> lrem(String key, long count, String value) {
    return new CommandObject<>(commandArguments(Command.LREM).key(key).add(count).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<Long> lrem(byte[] key, long count, byte[] value) {
    return new CommandObject<>(commandArguments(Command.LREM).key(key).add(count).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<String> lpop(String key) {
    return new CommandObject<>(commandArguments(Command.LPOP).key(key), BuilderFactory.STRING);
  }

  public final CommandObject<List<String>> lpop(String key, int count) {
    return new CommandObject<>(commandArguments(Command.LPOP).key(key).add(count), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<byte[]> lpop(byte[] key) {
    return new CommandObject<>(commandArguments(Command.LPOP).key(key), BuilderFactory.BINARY);
  }

  public final CommandObject<List<byte[]>> lpop(byte[] key, int count) {
    return new CommandObject<>(commandArguments(Command.LPOP).key(key).add(count), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<String> rpop(String key) {
    return new CommandObject<>(commandArguments(Command.RPOP).key(key), BuilderFactory.STRING);
  }

  public final CommandObject<List<String>> rpop(String key, int count) {
    return new CommandObject<>(commandArguments(Command.RPOP).key(key).add(count), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<byte[]> rpop(byte[] key) {
    return new CommandObject<>(commandArguments(Command.RPOP).key(key), BuilderFactory.BINARY);
  }

  public final CommandObject<List<byte[]>> rpop(byte[] key, int count) {
    return new CommandObject<>(commandArguments(Command.RPOP).key(key).add(count), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<Long> lpos(String key, String element) {
    return new CommandObject<>(commandArguments(Command.LPOS).key(key).add(element), BuilderFactory.LONG);
  }

  public final CommandObject<Long> lpos(String key, String element, LPosParams params) {
    return new CommandObject<>(commandArguments(Command.LPOS).key(key).add(element).addParams(params), BuilderFactory.LONG);
  }

  public final CommandObject<List<Long>> lpos(String key, String element, LPosParams params, long count) {
    return new CommandObject<>(commandArguments(Command.LPOS).key(key).add(element)
        .addParams(params).add(Keyword.COUNT).add(count), BuilderFactory.LONG_LIST);
  }

  public final CommandObject<Long> lpos(byte[] key, byte[] element) {
    return new CommandObject<>(commandArguments(Command.LPOS).key(key).add(element), BuilderFactory.LONG);
  }

  public final CommandObject<Long> lpos(byte[] key, byte[] element, LPosParams params) {
    return new CommandObject<>(commandArguments(Command.LPOS).key(key).add(element).addParams(params), BuilderFactory.LONG);
  }

  public final CommandObject<List<Long>> lpos(byte[] key, byte[] element, LPosParams params, long count) {
    return new CommandObject<>(commandArguments(Command.LPOS).key(key).add(element)
        .addParams(params).add(Keyword.COUNT).add(count), BuilderFactory.LONG_LIST);
  }

  public final CommandObject<Long> linsert(String key, ListPosition where, String pivot, String value) {
    return new CommandObject<>(commandArguments(Command.LINSERT).key(key).add(where)
        .add(pivot).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<Long> linsert(byte[] key, ListPosition where, byte[] pivot, byte[] value) {
    return new CommandObject<>(commandArguments(Command.LINSERT).key(key).add(where)
        .add(pivot).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<Long> lpushx(String key, String... strings) {
    return new CommandObject<>(commandArguments(Command.LPUSHX).key(key).addObjects((Object[]) strings), BuilderFactory.LONG);
  }

  public final CommandObject<Long> rpushx(String key, String... strings) {
    return new CommandObject<>(commandArguments(Command.RPUSHX).key(key).addObjects((Object[]) strings), BuilderFactory.LONG);
  }

  public final CommandObject<Long> lpushx(byte[] key, byte[]... args) {
    return new CommandObject<>(commandArguments(Command.LPUSHX).key(key).addObjects((Object[]) args), BuilderFactory.LONG);
  }

  public final CommandObject<Long> rpushx(byte[] key, byte[]... args) {
    return new CommandObject<>(commandArguments(Command.RPUSHX).key(key).addObjects((Object[]) args), BuilderFactory.LONG);
  }

  public final CommandObject<List<String>> blpop(int timeout, String key) {
    return new CommandObject<>(commandArguments(Command.BLPOP).blocking().key(key).add(timeout), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> blpop(int timeout, String... keys) {
    return new CommandObject<>(commandArguments(Command.BLPOP).blocking().keys((Object[]) keys).add(timeout), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<KeyValue<String, String>> blpop(double timeout, String key) {
    return new CommandObject<>(commandArguments(Command.BLPOP).blocking().key(key).add(timeout), BuilderFactory.KEYED_ELEMENT);
  }

  public final CommandObject<KeyValue<String, String>> blpop(double timeout, String... keys) {
    return new CommandObject<>(commandArguments(Command.BLPOP).blocking().keys((Object[]) keys).add(timeout), BuilderFactory.KEYED_ELEMENT);
  }

  public final CommandObject<List<byte[]>> blpop(int timeout, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.BLPOP).blocking().keys((Object[]) keys).add(timeout), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<KeyValue<byte[], byte[]>> blpop(double timeout, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.BLPOP).blocking().keys((Object[]) keys).add(timeout), BuilderFactory.BINARY_KEYED_ELEMENT);
  }

  public final CommandObject<List<String>> brpop(int timeout, String key) {
    return new CommandObject<>(commandArguments(Command.BRPOP).blocking().key(key).add(timeout), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> brpop(int timeout, String... keys) {
    return new CommandObject<>(commandArguments(Command.BRPOP).blocking().keys((Object[]) keys).add(timeout), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<KeyValue<String, String>> brpop(double timeout, String key) {
    return new CommandObject<>(commandArguments(Command.BRPOP).blocking().key(key).add(timeout), BuilderFactory.KEYED_ELEMENT);
  }

  public final CommandObject<KeyValue<String, String>> brpop(double timeout, String... keys) {
    return new CommandObject<>(commandArguments(Command.BRPOP).blocking().keys((Object[]) keys).add(timeout), BuilderFactory.KEYED_ELEMENT);
  }

  public final CommandObject<List<byte[]>> brpop(int timeout, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.BRPOP).blocking().keys((Object[]) keys).add(timeout), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<KeyValue<byte[], byte[]>> brpop(double timeout, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.BRPOP).blocking().keys((Object[]) keys).add(timeout), BuilderFactory.BINARY_KEYED_ELEMENT);
  }

  public final CommandObject<String> rpoplpush(String srckey, String dstkey) {
    return new CommandObject<>(commandArguments(Command.RPOPLPUSH).key(srckey).key(dstkey), BuilderFactory.STRING);
  }

  public final CommandObject<String> brpoplpush(String source, String destination, int timeout) {
    return new CommandObject<>(commandArguments(Command.BRPOPLPUSH).blocking().key(source)
        .key(destination).add(timeout), BuilderFactory.STRING);
  }

  public final CommandObject<byte[]> rpoplpush(byte[] srckey, byte[] dstkey) {
    return new CommandObject<>(commandArguments(Command.RPOPLPUSH).key(srckey).key(dstkey), BuilderFactory.BINARY);
  }

  public final CommandObject<byte[]> brpoplpush(byte[] source, byte[] destination, int timeout) {
    return new CommandObject<>(commandArguments(Command.BRPOPLPUSH).blocking().key(source)
        .key(destination).add(timeout), BuilderFactory.BINARY);
  }

  public final CommandObject<String> lmove(String srcKey, String dstKey, ListDirection from, ListDirection to) {
    return new CommandObject<>(commandArguments(Command.LMOVE).key(srcKey).key(dstKey)
        .add(from).add(to), BuilderFactory.STRING);
  }

  public final CommandObject<String> blmove(String srcKey, String dstKey, ListDirection from, ListDirection to, double timeout) {
    return new CommandObject<>(commandArguments(Command.BLMOVE).blocking().key(srcKey)
        .key(dstKey).add(from).add(to).add(timeout), BuilderFactory.STRING);
  }

  public final CommandObject<byte[]> lmove(byte[] srcKey, byte[] dstKey, ListDirection from, ListDirection to) {
    return new CommandObject<>(commandArguments(Command.LMOVE).key(srcKey).key(dstKey)
        .add(from).add(to), BuilderFactory.BINARY);
  }

  public final CommandObject<byte[]> blmove(byte[] srcKey, byte[] dstKey, ListDirection from, ListDirection to, double timeout) {
    return new CommandObject<>(commandArguments(Command.BLMOVE).blocking().key(srcKey)
        .key(dstKey).add(from).add(to).add(timeout), BuilderFactory.BINARY);
  }

  public final CommandObject<KeyValue<String, List<String>>> lmpop(ListDirection direction, String... keys) {
    return new CommandObject<>(commandArguments(Command.LMPOP).add(keys.length).keys((Object[]) keys)
        .add(direction), BuilderFactory.KEYED_STRING_LIST);
  }

  public final CommandObject<KeyValue<String, List<String>>> lmpop(ListDirection direction, int count, String... keys) {
    return new CommandObject<>(commandArguments(Command.LMPOP).add(keys.length).keys((Object[]) keys)
        .add(direction).add(Keyword.COUNT).add(count), BuilderFactory.KEYED_STRING_LIST);
  }

  public final CommandObject<KeyValue<String, List<String>>> blmpop(double timeout, ListDirection direction, String... keys) {
    return new CommandObject<>(commandArguments(Command.BLMPOP).blocking().add(timeout)
        .add(keys.length).keys((Object[]) keys).add(direction), BuilderFactory.KEYED_STRING_LIST);
  }

  public final CommandObject<KeyValue<String, List<String>>> blmpop(double timeout, ListDirection direction, int count, String... keys) {
    return new CommandObject<>(commandArguments(Command.BLMPOP).blocking().add(timeout)
        .add(keys.length).keys((Object[]) keys).add(direction).add(Keyword.COUNT).add(count),
        BuilderFactory.KEYED_STRING_LIST);
  }

  public final CommandObject<KeyValue<byte[], List<byte[]>>> lmpop(ListDirection direction, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.LMPOP).add(keys.length).keys((Object[]) keys)
        .add(direction), BuilderFactory.KEYED_BINARY_LIST);
  }

  public final CommandObject<KeyValue<byte[], List<byte[]>>> lmpop(ListDirection direction, int count, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.LMPOP).add(keys.length).keys((Object[]) keys)
        .add(direction).add(Keyword.COUNT).add(count), BuilderFactory.KEYED_BINARY_LIST);
  }

  public final CommandObject<KeyValue<byte[], List<byte[]>>> blmpop(double timeout, ListDirection direction, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.BLMPOP).blocking().add(timeout)
        .add(keys.length).keys((Object[]) keys).add(direction), BuilderFactory.KEYED_BINARY_LIST);
  }

  public final CommandObject<KeyValue<byte[], List<byte[]>>> blmpop(double timeout, ListDirection direction, int count, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.BLMPOP).blocking().add(timeout)
        .add(keys.length).keys((Object[]) keys).add(direction).add(Keyword.COUNT).add(count),
        BuilderFactory.KEYED_BINARY_LIST);
  }
  // List commands

  // Hash commands
  public final CommandObject<Long> hset(String key, String field, String value) {
    return new CommandObject<>(commandArguments(Command.HSET).key(key).add(field).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<Long> hset(String key, Map<String, String> hash) {
    return new CommandObject<>(addFlatMapArgs(commandArguments(Command.HSET).key(key), hash), BuilderFactory.LONG);
  }

  public final CommandObject<String> hget(String key, String field) {
    return new CommandObject<>(commandArguments(Command.HGET).key(key).add(field), BuilderFactory.STRING);
  }

  public final CommandObject<Long> hsetnx(String key, String field, String value) {
    return new CommandObject<>(commandArguments(Command.HSETNX).key(key).add(field).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<String> hmset(String key, Map<String, String> hash) {
    return new CommandObject<>(addFlatMapArgs(commandArguments(Command.HMSET).key(key), hash), BuilderFactory.STRING);
  }

  public final CommandObject<List<String>> hmget(String key, String... fields) {
    return new CommandObject<>(commandArguments(Command.HMGET).key(key).addObjects((Object[]) fields), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<Long> hset(byte[] key, byte[] field, byte[] value) {
    return new CommandObject<>(commandArguments(Command.HSET).key(key).add(field).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<Long> hset(byte[] key, Map<byte[], byte[]> hash) {
    return new CommandObject<>(addFlatMapArgs(commandArguments(Command.HSET).key(key), hash), BuilderFactory.LONG);
  }

  public final CommandObject<byte[]> hget(byte[] key, byte[] field) {
    return new CommandObject<>(commandArguments(Command.HGET).key(key).add(field), BuilderFactory.BINARY);
  }

  public final CommandObject<Long> hsetnx(byte[] key, byte[] field, byte[] value) {
    return new CommandObject<>(commandArguments(Command.HSETNX).key(key).add(field).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<String> hmset(byte[] key, Map<byte[], byte[]> hash) {
    return new CommandObject<>(addFlatMapArgs(commandArguments(Command.HMSET).key(key), hash), BuilderFactory.STRING);
  }

  public final CommandObject<List<byte[]>> hmget(byte[] key, byte[]... fields) {
    return new CommandObject<>(commandArguments(Command.HMGET).key(key).addObjects((Object[]) fields), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<Long> hincrBy(String key, String field, long value) {
    return new CommandObject<>(commandArguments(Command.HINCRBY).key(key).add(field).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<Double> hincrByFloat(String key, String field, double value) {
    return new CommandObject<>(commandArguments(Command.HINCRBYFLOAT).key(key).add(field).add(value), BuilderFactory.DOUBLE);
  }

  public final CommandObject<Boolean> hexists(String key, String field) {
    return new CommandObject<>(commandArguments(Command.HEXISTS).key(key).add(field), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Long> hdel(String key, String... field) {
    return new CommandObject<>(commandArguments(Command.HDEL).key(key).addObjects((Object[]) field), BuilderFactory.LONG);
  }

  public final CommandObject<Long> hlen(String key) {
    return new CommandObject<>(commandArguments(Command.HLEN).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> hincrBy(byte[] key, byte[] field, long value) {
    return new CommandObject<>(commandArguments(Command.HINCRBY).key(key).add(field).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<Double> hincrByFloat(byte[] key, byte[] field, double value) {
    return new CommandObject<>(commandArguments(Command.HINCRBYFLOAT).key(key).add(field).add(value), BuilderFactory.DOUBLE);
  }

  public final CommandObject<Boolean> hexists(byte[] key, byte[] field) {
    return new CommandObject<>(commandArguments(Command.HEXISTS).key(key).add(field), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Long> hdel(byte[] key, byte[]... field) {
    return new CommandObject<>(commandArguments(Command.HDEL).key(key).addObjects((Object[]) field), BuilderFactory.LONG);
  }

  public final CommandObject<Long> hlen(byte[] key) {
    return new CommandObject<>(commandArguments(Command.HLEN).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Set<String>> hkeys(String key) {
    return new CommandObject<>(commandArguments(Command.HKEYS).key(key), BuilderFactory.STRING_SET);
  }

  public final CommandObject<List<String>> hvals(String key) {
    return new CommandObject<>(commandArguments(Command.HVALS).key(key), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<Set<byte[]>> hkeys(byte[] key) {
    return new CommandObject<>(commandArguments(Command.HKEYS).key(key), BuilderFactory.BINARY_SET);
  }

  public final CommandObject<List<byte[]>> hvals(byte[] key) {
    return new CommandObject<>(commandArguments(Command.HVALS).key(key), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<Map<String, String>> hgetAll(String key) {
    return new CommandObject<>(commandArguments(Command.HGETALL).key(key), BuilderFactory.STRING_MAP);
  }

  public final CommandObject<String> hrandfield(String key) {
    return new CommandObject<>(commandArguments(Command.HRANDFIELD).key(key), BuilderFactory.STRING);
  }

  public final CommandObject<List<String>> hrandfield(String key, long count) {
    return new CommandObject<>(commandArguments(Command.HRANDFIELD).key(key).add(count), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<Map.Entry<String, String>>> hrandfieldWithValues(String key, long count) {
    return new CommandObject<>(commandArguments(Command.HRANDFIELD).key(key).add(count).add(Keyword.WITHVALUES),
        protocol != RedisProtocol.RESP3 ? BuilderFactory.STRING_PAIR_LIST : BuilderFactory.STRING_PAIR_LIST_FROM_PAIRS);
  }

  public final CommandObject<Map<byte[], byte[]>> hgetAll(byte[] key) {
    return new CommandObject<>(commandArguments(Command.HGETALL).key(key), BuilderFactory.BINARY_MAP);
  }

  public final CommandObject<byte[]> hrandfield(byte[] key) {
    return new CommandObject<>(commandArguments(Command.HRANDFIELD).key(key), BuilderFactory.BINARY);
  }

  public final CommandObject<List<byte[]>> hrandfield(byte[] key, long count) {
    return new CommandObject<>(commandArguments(Command.HRANDFIELD).key(key).add(count), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<Map.Entry<byte[], byte[]>>> hrandfieldWithValues(byte[] key, long count) {
    return new CommandObject<>(commandArguments(Command.HRANDFIELD).key(key).add(count).add(Keyword.WITHVALUES),
        protocol != RedisProtocol.RESP3 ? BuilderFactory.BINARY_PAIR_LIST : BuilderFactory.BINARY_PAIR_LIST_FROM_PAIRS);
  }

  public final CommandObject<ScanResult<Map.Entry<String, String>>> hscan(String key, String cursor, ScanParams params) {
    return new CommandObject<>(commandArguments(Command.HSCAN).key(key).add(cursor).addParams(params), BuilderFactory.HSCAN_RESPONSE);
  }

  public final CommandObject<ScanResult<String>> hscanNoValues(String key, String cursor, ScanParams params) {
    return new CommandObject<>(commandArguments(Command.HSCAN).key(key).add(cursor).addParams(params).add(
        Keyword.NOVALUES), BuilderFactory.SCAN_RESPONSE);
  }

  public final CommandObject<Long> hstrlen(String key, String field) {
    return new CommandObject<>(commandArguments(Command.HSTRLEN).key(key).add(field), BuilderFactory.LONG);
  }

  public final CommandObject<ScanResult<Map.Entry<byte[], byte[]>>> hscan(byte[] key, byte[] cursor, ScanParams params) {
    return new CommandObject<>(commandArguments(Command.HSCAN).key(key).add(cursor).addParams(params), BuilderFactory.HSCAN_BINARY_RESPONSE);
  }

  public final CommandObject<ScanResult<byte[]>> hscanNoValues(byte[] key, byte[] cursor, ScanParams params) {
    return new CommandObject<>(commandArguments(Command.HSCAN).key(key).add(cursor).addParams(params).add(
        Keyword.NOVALUES), BuilderFactory.SCAN_BINARY_RESPONSE);
  }

  public final CommandObject<Long> hstrlen(byte[] key, byte[] field) {
    return new CommandObject<>(commandArguments(Command.HSTRLEN).key(key).add(field), BuilderFactory.LONG);
  }
  // Hash commands

  // Set commands
  public final CommandObject<Long> sadd(String key, String... members) {
    return new CommandObject<>(commandArguments(Command.SADD).key(key).addObjects((Object[]) members), BuilderFactory.LONG);
  }

  public final CommandObject<Long> sadd(byte[] key, byte[]... members) {
    return new CommandObject<>(commandArguments(Command.SADD).key(key).addObjects((Object[]) members), BuilderFactory.LONG);
  }

  public final CommandObject<Set<String>> smembers(String key) {
    return new CommandObject<>(commandArguments(Command.SMEMBERS).key(key), BuilderFactory.STRING_SET);
  }

  public final CommandObject<Set<byte[]>> smembers(byte[] key) {
    return new CommandObject<>(commandArguments(Command.SMEMBERS).key(key), BuilderFactory.BINARY_SET);
  }

  public final CommandObject<Long> srem(String key, String... members) {
    return new CommandObject<>(commandArguments(Command.SREM).key(key).addObjects((Object[]) members), BuilderFactory.LONG);
  }

  public final CommandObject<Long> srem(byte[] key, byte[]... members) {
    return new CommandObject<>(commandArguments(Command.SREM).key(key).addObjects((Object[]) members), BuilderFactory.LONG);
  }

  public final CommandObject<String> spop(String key) {
    return new CommandObject<>(commandArguments(Command.SPOP).key(key), BuilderFactory.STRING);
  }

  public final CommandObject<byte[]> spop(byte[] key) {
    return new CommandObject<>(commandArguments(Command.SPOP).key(key), BuilderFactory.BINARY);
  }

  public final CommandObject<Set<String>> spop(String key, long count) {
    return new CommandObject<>(commandArguments(Command.SPOP).key(key).add(count), BuilderFactory.STRING_SET);
  }

  public final CommandObject<Set<byte[]>> spop(byte[] key, long count) {
    return new CommandObject<>(commandArguments(Command.SPOP).key(key).add(count), BuilderFactory.BINARY_SET);
  }

  public final CommandObject<Long> scard(String key) {
    return new CommandObject<>(commandArguments(Command.SCARD).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> scard(byte[] key) {
    return new CommandObject<>(commandArguments(Command.SCARD).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Boolean> sismember(String key, String member) {
    return new CommandObject<>(commandArguments(Command.SISMEMBER).key(key).add(member), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Boolean> sismember(byte[] key, byte[] member) {
    return new CommandObject<>(commandArguments(Command.SISMEMBER).key(key).add(member), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<List<Boolean>> smismember(String key, String... members) {
    return new CommandObject<>(commandArguments(Command.SMISMEMBER).key(key).addObjects((Object[]) members), BuilderFactory.BOOLEAN_LIST);
  }

  public final CommandObject<List<Boolean>> smismember(byte[] key, byte[]... members) {
    return new CommandObject<>(commandArguments(Command.SMISMEMBER).key(key).addObjects((Object[]) members), BuilderFactory.BOOLEAN_LIST);
  }

  public final CommandObject<String> srandmember(String key) {
    return new CommandObject<>(commandArguments(Command.SRANDMEMBER).key(key), BuilderFactory.STRING);
  }

  public final CommandObject<byte[]> srandmember(byte[] key) {
    return new CommandObject<>(commandArguments(Command.SRANDMEMBER).key(key), BuilderFactory.BINARY);
  }

  public final CommandObject<List<String>> srandmember(String key, int count) {
    return new CommandObject<>(commandArguments(Command.SRANDMEMBER).key(key).add(count), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<byte[]>> srandmember(byte[] key, int count) {
    return new CommandObject<>(commandArguments(Command.SRANDMEMBER).key(key).add(count), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<ScanResult<String>> sscan(String key, String cursor, ScanParams params) {
    return new CommandObject<>(commandArguments(Command.SSCAN).key(key).add(cursor).addParams(params), BuilderFactory.SSCAN_RESPONSE);
  }

  public final CommandObject<ScanResult<byte[]>> sscan(byte[] key, byte[] cursor, ScanParams params) {
    return new CommandObject<>(commandArguments(Command.SSCAN).key(key).add(cursor).addParams(params), BuilderFactory.SSCAN_BINARY_RESPONSE);
  }

  public final CommandObject<Set<String>> sdiff(String... keys) {
    return new CommandObject<>(commandArguments(Command.SDIFF).keys((Object[]) keys), BuilderFactory.STRING_SET);
  }

  public final CommandObject<Long> sdiffstore(String dstkey, String... keys) {
    return new CommandObject<>(commandArguments(Command.SDIFFSTORE).key(dstkey).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Set<byte[]>> sdiff(byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.SDIFF).keys((Object[]) keys), BuilderFactory.BINARY_SET);
  }

  public final CommandObject<Long> sdiffstore(byte[] dstkey, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.SDIFFSTORE).key(dstkey).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Set<String>> sinter(String... keys) {
    return new CommandObject<>(commandArguments(Command.SINTER).keys((Object[]) keys), BuilderFactory.STRING_SET);
  }

  public final CommandObject<Long> sinterstore(String dstkey, String... keys) {
    return new CommandObject<>(commandArguments(Command.SINTERSTORE).key(dstkey).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Long> sintercard(String... keys) {
    return new CommandObject<>(commandArguments(Command.SINTERCARD).add(keys.length).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Long> sintercard(int limit, String... keys) {
    return new CommandObject<>(commandArguments(Command.SINTERCARD).add(keys.length).keys((Object[]) keys).add(
        Keyword.LIMIT).add(limit),BuilderFactory.LONG);
  }

  public final CommandObject<Set<byte[]>> sinter(byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.SINTER).keys((Object[]) keys), BuilderFactory.BINARY_SET);
  }

  public final CommandObject<Long> sinterstore(byte[] dstkey, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.SINTERSTORE).key(dstkey).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Long> sintercard(byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.SINTERCARD).add(keys.length).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Long> sintercard(int limit, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.SINTERCARD).add(keys.length).keys((Object[]) keys).add(
        Keyword.LIMIT).add(limit),BuilderFactory.LONG);
  }

  public final CommandObject<Set<String>> sunion(String... keys) {
    return new CommandObject<>(commandArguments(Command.SUNION).keys((Object[]) keys), BuilderFactory.STRING_SET);
  }

  public final CommandObject<Long> sunionstore(String dstkey, String... keys) {
    return new CommandObject<>(commandArguments(Command.SUNIONSTORE).key(dstkey).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Set<byte[]>> sunion(byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.SUNION).keys((Object[]) keys), BuilderFactory.BINARY_SET);
  }

  public final CommandObject<Long> sunionstore(byte[] dstkey, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.SUNIONSTORE).key(dstkey).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Long> smove(String srckey, String dstkey, String member) {
    return new CommandObject<>(commandArguments(Command.SMOVE).key(srckey).key(dstkey).add(member), BuilderFactory.LONG);
  }

  public final CommandObject<Long> smove(byte[] srckey, byte[] dstkey, byte[] member) {
    return new CommandObject<>(commandArguments(Command.SMOVE).key(srckey).key(dstkey).add(member), BuilderFactory.LONG);
  }
  // Set commands

  // Sorted Set commands
  public final CommandObject<Long> zadd(String key, double score, String member) {
    return new CommandObject<>(commandArguments(Command.ZADD).key(key).add(score).add(member), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zadd(String key, double score, String member, ZAddParams params) {
    return new CommandObject<>(commandArguments(Command.ZADD).key(key).addParams(params)
        .add(score).add(member), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zadd(String key, Map<String, Double> scoreMembers) {
    return new CommandObject<>(addSortedSetFlatMapArgs(commandArguments(Command.ZADD).key(key), scoreMembers), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zadd(String key, Map<String, Double> scoreMembers, ZAddParams params) {
    return new CommandObject<>(addSortedSetFlatMapArgs(commandArguments(Command.ZADD).key(key).addParams(params), scoreMembers), BuilderFactory.LONG);
  }

  public final CommandObject<Double> zaddIncr(String key, double score, String member, ZAddParams params) {
    return new CommandObject<>(commandArguments(Command.ZADD).key(key).add(Keyword.INCR)
        .addParams(params).add(score).add(member), BuilderFactory.DOUBLE);
  }

  public final CommandObject<Long> zadd(byte[] key, double score, byte[] member) {
    return new CommandObject<>(commandArguments(Command.ZADD).key(key).add(score).add(member), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zadd(byte[] key, double score, byte[] member, ZAddParams params) {
    return new CommandObject<>(commandArguments(Command.ZADD).key(key).addParams(params)
        .add(score).add(member), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zadd(byte[] key, Map<byte[], Double> scoreMembers) {
    return new CommandObject<>(addSortedSetFlatMapArgs(commandArguments(Command.ZADD).key(key), scoreMembers), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zadd(byte[] key, Map<byte[], Double> scoreMembers, ZAddParams params) {
    return new CommandObject<>(addSortedSetFlatMapArgs(commandArguments(Command.ZADD).key(key).addParams(params), scoreMembers), BuilderFactory.LONG);
  }

  public final CommandObject<Double> zaddIncr(byte[] key, double score, byte[] member, ZAddParams params) {
    return new CommandObject<>(commandArguments(Command.ZADD).key(key).add(Keyword.INCR)
        .addParams(params).add(score).add(member), BuilderFactory.DOUBLE);
  }

  public final CommandObject<Double> zincrby(String key, double increment, String member) {
    return new CommandObject<>(commandArguments(Command.ZINCRBY).key(key).add(increment).add(member), BuilderFactory.DOUBLE);
  }

  public final CommandObject<Double> zincrby(String key, double increment, String member, ZIncrByParams params) {
    return new CommandObject<>(commandArguments(Command.ZADD).key(key).addParams(params).add(increment).add(member), BuilderFactory.DOUBLE);
  }

  public final CommandObject<Double> zincrby(byte[] key, double increment, byte[] member) {
    return new CommandObject<>(commandArguments(Command.ZINCRBY).key(key).add(increment).add(member), BuilderFactory.DOUBLE);
  }

  public final CommandObject<Double> zincrby(byte[] key, double increment, byte[] member, ZIncrByParams params) {
    return new CommandObject<>(commandArguments(Command.ZADD).key(key).addParams(params).add(increment).add(member), BuilderFactory.DOUBLE);
  }

  public final CommandObject<Long> zrem(String key, String... members) {
    return new CommandObject<>(commandArguments(Command.ZREM).key(key).addObjects((Object[]) members), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zrem(byte[] key, byte[]... members) {
    return new CommandObject<>(commandArguments(Command.ZREM).key(key).addObjects((Object[]) members), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zrank(String key, String member) {
    return new CommandObject<>(commandArguments(Command.ZRANK).key(key).add(member), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zrevrank(String key, String member) {
    return new CommandObject<>(commandArguments(Command.ZREVRANK).key(key).add(member), BuilderFactory.LONG);
  }

  public final CommandObject<KeyValue<Long, Double>> zrankWithScore(String key, String member) {
    return new CommandObject<>(commandArguments(Command.ZRANK).key(key).add(member).add(Keyword.WITHSCORE), BuilderFactory.ZRANK_WITHSCORE_PAIR);
  }

  public final CommandObject<KeyValue<Long, Double>> zrevrankWithScore(String key, String member) {
    return new CommandObject<>(commandArguments(Command.ZREVRANK).key(key).add(member).add(Keyword.WITHSCORE), BuilderFactory.ZRANK_WITHSCORE_PAIR);
  }

  public final CommandObject<Long> zrank(byte[] key, byte[] member) {
    return new CommandObject<>(commandArguments(Command.ZRANK).key(key).add(member), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zrevrank(byte[] key, byte[] member) {
    return new CommandObject<>(commandArguments(Command.ZREVRANK).key(key).add(member), BuilderFactory.LONG);
  }

  public final CommandObject<KeyValue<Long, Double>> zrankWithScore(byte[] key, byte[] member) {
    return new CommandObject<>(commandArguments(Command.ZRANK).key(key).add(member).add(Keyword.WITHSCORE), BuilderFactory.ZRANK_WITHSCORE_PAIR);
  }

  public final CommandObject<KeyValue<Long, Double>> zrevrankWithScore(byte[] key, byte[] member) {
    return new CommandObject<>(commandArguments(Command.ZREVRANK).key(key).add(member).add(Keyword.WITHSCORE), BuilderFactory.ZRANK_WITHSCORE_PAIR);
  }

  public final CommandObject<String> zrandmember(String key) {
    return new CommandObject<>(commandArguments(Command.ZRANDMEMBER).key(key), BuilderFactory.STRING);
  }

  public final CommandObject<List<String>> zrandmember(String key, long count) {
    return new CommandObject<>(commandArguments(Command.ZRANDMEMBER).key(key).add(count), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<Tuple>> zrandmemberWithScores(String key, long count) {
    return new CommandObject<>(commandArguments(Command.ZRANDMEMBER).key(key).add(count).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<byte[]> zrandmember(byte[] key) {
    return new CommandObject<>(commandArguments(Command.ZRANDMEMBER).key(key), BuilderFactory.BINARY);
  }

  public final CommandObject<List<byte[]>> zrandmember(byte[] key, long count) {
    return new CommandObject<>(commandArguments(Command.ZRANDMEMBER).key(key).add(count), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<Tuple>> zrandmemberWithScores(byte[] key, long count) {
    return new CommandObject<>(commandArguments(Command.ZRANDMEMBER).key(key).add(count).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<Long> zcard(String key) {
    return new CommandObject<>(commandArguments(Command.ZCARD).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Double> zscore(String key, String member) {
    return new CommandObject<>(commandArguments(Command.ZSCORE).key(key).add(member), BuilderFactory.DOUBLE);
  }

  public final CommandObject<List<Double>> zmscore(String key, String... members) {
    return new CommandObject<>(commandArguments(Command.ZMSCORE).key(key).addObjects((Object[]) members), BuilderFactory.DOUBLE_LIST);
  }

  public final CommandObject<Long> zcard(byte[] key) {
    return new CommandObject<>(commandArguments(Command.ZCARD).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Double> zscore(byte[] key, byte[] member) {
    return new CommandObject<>(commandArguments(Command.ZSCORE).key(key).add(member), BuilderFactory.DOUBLE);
  }

  public final CommandObject<List<Double>> zmscore(byte[] key, byte[]... members) {
    return new CommandObject<>(commandArguments(Command.ZMSCORE).key(key).addObjects((Object[]) members), BuilderFactory.DOUBLE_LIST);
  }

  public final CommandObject<Tuple> zpopmax(String key) {
    return new CommandObject<>(commandArguments(Command.ZPOPMAX).key(key), BuilderFactory.TUPLE);
  }

  public final CommandObject<List<Tuple>> zpopmax(String key, int count) {
    return new CommandObject<>(commandArguments(Command.ZPOPMAX).key(key).add(count), getTupleListBuilder());
  }

  public final CommandObject<Tuple> zpopmin(String key) {
    return new CommandObject<>(commandArguments(Command.ZPOPMIN).key(key), BuilderFactory.TUPLE);
  }

  public final CommandObject<List<Tuple>> zpopmin(String key, int count) {
    return new CommandObject<>(commandArguments(Command.ZPOPMIN).key(key).add(count), getTupleListBuilder());
  }

  public final CommandObject<Tuple> zpopmax(byte[] key) {
    return new CommandObject<>(commandArguments(Command.ZPOPMAX).key(key), BuilderFactory.TUPLE);
  }

  public final CommandObject<List<Tuple>> zpopmax(byte[] key, int count) {
    return new CommandObject<>(commandArguments(Command.ZPOPMAX).key(key).add(count), getTupleListBuilder());
  }

  public final CommandObject<Tuple> zpopmin(byte[] key) {
    return new CommandObject<>(commandArguments(Command.ZPOPMIN).key(key), BuilderFactory.TUPLE);
  }

  public final CommandObject<List<Tuple>> zpopmin(byte[] key, int count) {
    return new CommandObject<>(commandArguments(Command.ZPOPMIN).key(key).add(count), getTupleListBuilder());
  }

  public final CommandObject<KeyValue<String, Tuple>> bzpopmax(double timeout, String... keys) {
    return new CommandObject<>(commandArguments(Command.BZPOPMAX).blocking().keys((Object[]) keys).add(timeout),
        BuilderFactory.KEYED_TUPLE);
  }

  public final CommandObject<KeyValue<String, Tuple>> bzpopmin(double timeout, String... keys) {
    return new CommandObject<>(commandArguments(Command.BZPOPMIN).blocking().keys((Object[]) keys).add(timeout),
        BuilderFactory.KEYED_TUPLE);
  }

  public final CommandObject<KeyValue<byte[], Tuple>> bzpopmax(double timeout, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.BZPOPMAX).blocking().keys((Object[]) keys)
        .add(timeout), BuilderFactory.BINARY_KEYED_TUPLE);
  }

  public final CommandObject<KeyValue<byte[], Tuple>> bzpopmin(double timeout, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.BZPOPMIN).blocking().keys((Object[]) keys)
        .add(timeout), BuilderFactory.BINARY_KEYED_TUPLE);
  }

  public final CommandObject<Long> zcount(String key, double min, double max) {
    return new CommandObject<>(commandArguments(Command.ZCOUNT).key(key).add(min).add(max), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zcount(String key, String min, String max) {
    return new CommandObject<>(commandArguments(Command.ZCOUNT).key(key).add(min).add(max), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zcount(byte[] key, double min, double max) {
    return new CommandObject<>(commandArguments(Command.ZCOUNT).key(key).add(min).add(max), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zcount(byte[] key, byte[] min, byte[] max) {
    return new CommandObject<>(commandArguments(Command.ZCOUNT).key(key).add(min).add(max), BuilderFactory.LONG);
  }

  public final CommandObject<List<String>> zrange(String key, long start, long stop) {
    return new CommandObject<>(commandArguments(Command.ZRANGE).key(key).add(start).add(stop), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> zrevrange(String key, long start, long stop) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGE).key(key).add(start).add(stop), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<Tuple>> zrangeWithScores(String key, long start, long stop) {
    return new CommandObject<>(commandArguments(Command.ZRANGE).key(key)
        .add(start).add(stop).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrevrangeWithScores(String key, long start, long stop) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGE).key(key)
        .add(start).add(stop).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<String>> zrange(String key, ZRangeParams zRangeParams) {
    return new CommandObject<>(commandArguments(Command.ZRANGE).key(key).addParams(zRangeParams), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<Tuple>> zrangeWithScores(String key, ZRangeParams zRangeParams) {
    return new CommandObject<>(commandArguments(Command.ZRANGE).key(key).addParams(zRangeParams).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<Long> zrangestore(String dest, String src, ZRangeParams zRangeParams) {
    return new CommandObject<>(commandArguments(Command.ZRANGESTORE).key(dest).add(src).addParams(zRangeParams), BuilderFactory.LONG);
  }

  public final CommandObject<List<String>> zrangeByScore(String key, double min, double max) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> zrangeByScore(String key, String min, String max) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> zrevrangeByScore(String key, double max, double min) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> zrevrangeByScore(String key, String max, String min) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> zrangeByScore(String key, double min, double max, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max)
        .add(Keyword.LIMIT).add(offset).add(count), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> zrangeByScore(String key, String min, String max, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max)
        .add(Keyword.LIMIT).add(offset).add(count), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> zrevrangeByScore(String key, double max, double min, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min)
        .add(Keyword.LIMIT).add(offset).add(count), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> zrevrangeByScore(String key, String max, String min, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min)
        .add(Keyword.LIMIT).add(offset).add(count), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<Tuple>> zrangeByScoreWithScores(String key, double min, double max) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max)
        .add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrangeByScoreWithScores(String key, String min, String max) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max)
        .add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrevrangeByScoreWithScores(String key, double max, double min) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min)
        .add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrevrangeByScoreWithScores(String key, String max, String min) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min)
        .add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrangeByScoreWithScores(String key, double min, double max, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max)
        .add(Keyword.LIMIT).add(offset).add(count).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrangeByScoreWithScores(String key, String min, String max, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max)
        .add(Keyword.LIMIT).add(offset).add(count).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrevrangeByScoreWithScores(String key, double max, double min, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min)
        .add(Keyword.LIMIT).add(offset).add(count).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrevrangeByScoreWithScores(String key, String max, String min, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min)
        .add(Keyword.LIMIT).add(offset).add(count).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<byte[]>> zrange(byte[] key, long start, long stop) {
    return new CommandObject<>(commandArguments(Command.ZRANGE).key(key).add(start).add(stop), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<byte[]>> zrevrange(byte[] key, long start, long stop) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGE).key(key).add(start).add(stop), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<Tuple>> zrangeWithScores(byte[] key, long start, long stop) {
    return new CommandObject<>(commandArguments(Command.ZRANGE).key(key)
        .add(start).add(stop).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrevrangeWithScores(byte[] key, long start, long stop) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGE).key(key)
        .add(start).add(stop).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<byte[]>> zrange(byte[] key, ZRangeParams zRangeParams) {
    return new CommandObject<>(commandArguments(Command.ZRANGE).key(key).addParams(zRangeParams), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<Tuple>> zrangeWithScores(byte[] key, ZRangeParams zRangeParams) {
    return new CommandObject<>(commandArguments(Command.ZRANGE).key(key).addParams(zRangeParams).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<Long> zrangestore(byte[] dest, byte[] src, ZRangeParams zRangeParams) {
    return new CommandObject<>(commandArguments(Command.ZRANGESTORE).key(dest).add(src).addParams(zRangeParams), BuilderFactory.LONG);
  }

  public final CommandObject<List<byte[]>> zrangeByScore(byte[] key, double min, double max) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<byte[]>> zrangeByScore(byte[] key, byte[] min, byte[] max) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<byte[]>> zrevrangeByScore(byte[] key, double max, double min) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<byte[]>> zrevrangeByScore(byte[] key, byte[] max, byte[] min) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<byte[]>> zrangeByScore(byte[] key, double min, double max, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max)
        .add(Keyword.LIMIT).add(offset).add(count), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<byte[]>> zrangeByScore(byte[] key, byte[] min, byte[] max, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max)
        .add(Keyword.LIMIT).add(offset).add(count), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<byte[]>> zrevrangeByScore(byte[] key, double max, double min, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min)
        .add(Keyword.LIMIT).add(offset).add(count), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<byte[]>> zrevrangeByScore(byte[] key, byte[] max, byte[] min, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min)
        .add(Keyword.LIMIT).add(offset).add(count), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<Tuple>> zrangeByScoreWithScores(byte[] key, double min, double max) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max)
        .add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrangeByScoreWithScores(byte[] key, byte[] min, byte[] max) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max)
        .add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrevrangeByScoreWithScores(byte[] key, double max, double min) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min)
        .add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrevrangeByScoreWithScores(byte[] key, byte[] max, byte[] min) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min)
        .add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrangeByScoreWithScores(byte[] key, double min, double max, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max)
        .add(Keyword.LIMIT).add(offset).add(count).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrangeByScoreWithScores(byte[] key, byte[] min, byte[] max, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYSCORE).key(key).add(min).add(max)
        .add(Keyword.LIMIT).add(offset).add(count).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrevrangeByScoreWithScores(byte[] key, double max, double min, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min)
        .add(Keyword.LIMIT).add(offset).add(count).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<List<Tuple>> zrevrangeByScoreWithScores(byte[] key, byte[] max, byte[] min, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYSCORE).key(key).add(max).add(min)
        .add(Keyword.LIMIT).add(offset).add(count).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<Long> zremrangeByRank(String key, long start, long stop) {
    return new CommandObject<>(commandArguments(Command.ZREMRANGEBYRANK).key(key).add(start).add(stop), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zremrangeByScore(String key, double min, double max) {
    return new CommandObject<>(commandArguments(Command.ZREMRANGEBYSCORE).key(key).add(min).add(max), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zremrangeByScore(String key, String min, String max) {
    return new CommandObject<>(commandArguments(Command.ZREMRANGEBYSCORE).key(key).add(min).add(max), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zremrangeByRank(byte[] key, long start, long stop) {
    return new CommandObject<>(commandArguments(Command.ZREMRANGEBYRANK).key(key).add(start).add(stop), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zremrangeByScore(byte[] key, double min, double max) {
    return new CommandObject<>(commandArguments(Command.ZREMRANGEBYSCORE).key(key).add(min).add(max), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zremrangeByScore(byte[] key, byte[] min, byte[] max) {
    return new CommandObject<>(commandArguments(Command.ZREMRANGEBYSCORE).key(key).add(min).add(max), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zlexcount(String key, String min, String max) {
    return new CommandObject<>(commandArguments(Command.ZLEXCOUNT).key(key).add(min).add(max), BuilderFactory.LONG);
  }

  public final CommandObject<List<String>> zrangeByLex(String key, String min, String max) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYLEX).key(key).add(min).add(max), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> zrangeByLex(String key, String min, String max, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYLEX).key(key).add(min).add(max)
        .add(Keyword.LIMIT).add(offset).add(count), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> zrevrangeByLex(String key, String max, String min) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYLEX).key(key).add(max).add(min), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> zrevrangeByLex(String key, String max, String min, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYLEX).key(key).add(max).add(min)
        .add(Keyword.LIMIT).add(offset).add(count), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<Long> zremrangeByLex(String key, String min, String max) {
    return new CommandObject<>(commandArguments(Command.ZREMRANGEBYLEX).key(key).add(min).add(max), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zlexcount(byte[] key, byte[] min, byte[] max) {
    return new CommandObject<>(commandArguments(Command.ZLEXCOUNT).key(key).add(min).add(max), BuilderFactory.LONG);
  }

  public final CommandObject<List<byte[]>> zrangeByLex(byte[] key, byte[] min, byte[] max) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYLEX).key(key).add(min).add(max), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<byte[]>> zrangeByLex(byte[] key, byte[] min, byte[] max, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZRANGEBYLEX).key(key).add(min).add(max)
        .add(Keyword.LIMIT).add(offset).add(count), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<byte[]>> zrevrangeByLex(byte[] key, byte[] max, byte[] min) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYLEX).key(key).add(max).add(min), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<byte[]>> zrevrangeByLex(byte[] key, byte[] max, byte[] min, int offset, int count) {
    return new CommandObject<>(commandArguments(Command.ZREVRANGEBYLEX).key(key).add(max).add(min)
        .add(Keyword.LIMIT).add(offset).add(count), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<Long> zremrangeByLex(byte[] key, byte[] min, byte[] max) {
    return new CommandObject<>(commandArguments(Command.ZREMRANGEBYLEX).key(key).add(min).add(max), BuilderFactory.LONG);
  }

  public final CommandObject<ScanResult<Tuple>> zscan(String key, String cursor, ScanParams params) {
    return new CommandObject<>(commandArguments(Command.ZSCAN).key(key).add(cursor).addParams(params), BuilderFactory.ZSCAN_RESPONSE);
  }

  public final CommandObject<ScanResult<Tuple>> zscan(byte[] key, byte[] cursor, ScanParams params) {
    return new CommandObject<>(commandArguments(Command.ZSCAN).key(key).add(cursor).addParams(params), BuilderFactory.ZSCAN_RESPONSE);
  }

  public final CommandObject<List<String>> zdiff(String... keys) {
    return new CommandObject<>(commandArguments(Command.ZDIFF).add(keys.length).keys((Object[]) keys),
        BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<Tuple>> zdiffWithScores(String... keys) {
    return new CommandObject<>(commandArguments(Command.ZDIFF).add(keys.length).keys((Object[]) keys)
        .add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  /**
   * @deprecated Use {@link #zdiffstore(java.lang.String, java.lang.String...)}.
   */
  @Deprecated
  public final CommandObject<Long> zdiffStore(String dstkey, String... keys) {
    return zdiffstore(dstkey, keys);
  }

  public final CommandObject<Long> zdiffstore(String dstkey, String... keys) {
    return new CommandObject<>(commandArguments(Command.ZDIFFSTORE).key(dstkey)
        .add(keys.length).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<List<byte[]>> zdiff(byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.ZDIFF).add(keys.length).keys((Object[]) keys), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<Tuple>> zdiffWithScores(byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.ZDIFF).add(keys.length).keys((Object[]) keys)
        .add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  /**
   * @deprecated Use {@link #zdiffstore(byte..., byte[]...)}.
   */
  @Deprecated
  public final CommandObject<Long> zdiffStore(byte[] dstkey, byte[]... keys) {
    return zdiffstore(dstkey, keys);
  }

  public final CommandObject<Long> zdiffstore(byte[] dstkey, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.ZDIFFSTORE).key(dstkey)
        .add(keys.length).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<List<String>> zinter(ZParams params, String... keys) {
    return new CommandObject<>(commandArguments(Command.ZINTER).add(keys.length).keys((Object[]) keys)
        .addParams(params), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<Tuple>> zinterWithScores(ZParams params, String... keys) {
    return new CommandObject<>(commandArguments(Command.ZINTER).add(keys.length).keys((Object[]) keys)
        .addParams(params).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<Long> zinterstore(String dstkey, String... keys) {
    return new CommandObject<>(commandArguments(Command.ZINTERSTORE).key(dstkey)
        .add(keys.length).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zinterstore(String dstkey, ZParams params, String... keys) {
    return new CommandObject<>(commandArguments(Command.ZINTERSTORE).key(dstkey)
        .add(keys.length).keys((Object[]) keys).addParams(params), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zintercard(String... keys) {
    return new CommandObject<>(commandArguments(Command.ZINTERCARD).add(keys.length)
        .keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zintercard(long limit, String... keys) {
    return new CommandObject<>(commandArguments(Command.ZINTERCARD).add(keys.length)
        .keys((Object[]) keys).add(Keyword.LIMIT).add(limit), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zinterstore(byte[] dstkey, byte[]... sets) {
    return new CommandObject<>(commandArguments(Command.ZINTERSTORE).key(dstkey)
        .add(sets.length).keys((Object[]) sets), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zinterstore(byte[] dstkey, ZParams params, byte[]... sets) {
    return new CommandObject<>(commandArguments(Command.ZINTERSTORE).key(dstkey)
        .add(sets.length).keys((Object[]) sets).addParams(params), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zintercard(byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.ZINTERCARD).add(keys.length)
        .keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zintercard(long limit, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.ZINTERCARD).add(keys.length)
        .keys((Object[]) keys).add(Keyword.LIMIT).add(limit), BuilderFactory.LONG);
  }

  public final CommandObject<List<byte[]>> zinter(ZParams params, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.ZINTER).add(keys.length).keys((Object[]) keys)
        .addParams(params), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<Tuple>> zinterWithScores(ZParams params, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.ZINTER).add(keys.length).keys((Object[]) keys)
        .addParams(params).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<Long> zunionstore(String dstkey, String... sets) {
    return new CommandObject<>(commandArguments(Command.ZUNIONSTORE).key(dstkey)
        .add(sets.length).keys((Object[]) sets), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zunionstore(String dstkey, ZParams params, String... sets) {
    return new CommandObject<>(commandArguments(Command.ZUNIONSTORE).key(dstkey)
        .add(sets.length).keys((Object[]) sets).addParams(params), BuilderFactory.LONG);
  }

  public final CommandObject<List<String>> zunion(ZParams params, String... keys) {
    return new CommandObject<>(commandArguments(Command.ZUNION).add(keys.length).keys((Object[]) keys)
        .addParams(params), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<Tuple>> zunionWithScores(ZParams params, String... keys) {
    return new CommandObject<>(commandArguments(Command.ZUNION).add(keys.length).keys((Object[]) keys)
        .addParams(params).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<Long> zunionstore(byte[] dstkey, byte[]... sets) {
    return new CommandObject<>(commandArguments(Command.ZUNIONSTORE).key(dstkey)
        .add(sets.length).keys((Object[]) sets), BuilderFactory.LONG);
  }

  public final CommandObject<Long> zunionstore(byte[] dstkey, ZParams params, byte[]... sets) {
    return new CommandObject<>(commandArguments(Command.ZUNIONSTORE).key(dstkey)
        .add(sets.length).keys((Object[]) sets).addParams(params), BuilderFactory.LONG);
  }

  public final CommandObject<List<byte[]>> zunion(ZParams params, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.ZUNION).add(keys.length).keys((Object[]) keys)
        .addParams(params), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<Tuple>> zunionWithScores(ZParams params, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.ZUNION).add(keys.length).keys((Object[]) keys)
        .addParams(params).add(Keyword.WITHSCORES), getTupleListBuilder());
  }

  public final CommandObject<KeyValue<String, List<Tuple>>> zmpop(SortedSetOption option, String... keys) {
    return new CommandObject<>(commandArguments(Command.ZMPOP).add(keys.length).keys((Object[]) keys)
        .add(option), BuilderFactory.KEYED_TUPLE_LIST);
  }

  public final CommandObject<KeyValue<String, List<Tuple>>> zmpop(SortedSetOption option, int count, String... keys) {
    return new CommandObject<>(commandArguments(Command.ZMPOP).add(keys.length).keys((Object[]) keys)
        .add(option).add(Keyword.COUNT).add(count), BuilderFactory.KEYED_TUPLE_LIST);
  }

  public final CommandObject<KeyValue<String, List<Tuple>>> bzmpop(double timeout, SortedSetOption option, String... keys) {
    return new CommandObject<>(commandArguments(Command.BZMPOP).blocking().add(timeout).add(keys.length)
        .keys((Object[]) keys).add(option), BuilderFactory.KEYED_TUPLE_LIST);
  }

  public final CommandObject<KeyValue<String, List<Tuple>>> bzmpop(double timeout, SortedSetOption option, int count, String... keys) {
    return new CommandObject<>(commandArguments(Command.BZMPOP).blocking().add(timeout).add(keys.length)
        .keys((Object[]) keys).add(option).add(Keyword.COUNT).add(count), BuilderFactory.KEYED_TUPLE_LIST);
  }

  public final CommandObject<KeyValue<byte[], List<Tuple>>> zmpop(SortedSetOption option, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.ZMPOP).add(keys.length).keys((Object[]) keys)
        .add(option), BuilderFactory.BINARY_KEYED_TUPLE_LIST);
  }

  public final CommandObject<KeyValue<byte[], List<Tuple>>> zmpop(SortedSetOption option, int count, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.ZMPOP).add(keys.length).keys((Object[]) keys)
        .add(option).add(Keyword.COUNT).add(count), BuilderFactory.BINARY_KEYED_TUPLE_LIST);
  }

  public final CommandObject<KeyValue<byte[], List<Tuple>>> bzmpop(double timeout, SortedSetOption option, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.BZMPOP).blocking().add(timeout).add(keys.length)
        .keys((Object[]) keys).add(option), BuilderFactory.BINARY_KEYED_TUPLE_LIST);
  }

  public final CommandObject<KeyValue<byte[], List<Tuple>>> bzmpop(double timeout, SortedSetOption option, int count, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.BZMPOP).blocking().add(timeout).add(keys.length)
        .keys((Object[]) keys).add(option).add(Keyword.COUNT).add(count), BuilderFactory.BINARY_KEYED_TUPLE_LIST);
  }

  private Builder<List<Tuple>> getTupleListBuilder() {
    return protocol == RedisProtocol.RESP3 ? BuilderFactory.TUPLE_LIST_RESP3 : BuilderFactory.TUPLE_LIST;
  }

  private Builder<Set<Tuple>> getTupleSetBuilder() {
    return protocol == RedisProtocol.RESP3 ? BuilderFactory.TUPLE_ZSET_RESP3 : BuilderFactory.TUPLE_ZSET;
  }
  // Sorted Set commands

  // Geo commands
  public final CommandObject<Long> geoadd(String key, double longitude, double latitude, String member) {
    return new CommandObject<>(commandArguments(Command.GEOADD).key(key).add(longitude).add(latitude).add(member), BuilderFactory.LONG);
  }

  public final CommandObject<Long> geoadd(String key, Map<String, GeoCoordinate> memberCoordinateMap) {
    return new CommandObject<>(addGeoCoordinateFlatMapArgs(commandArguments(Command.GEOADD).key(key), memberCoordinateMap), BuilderFactory.LONG);
  }

  public final CommandObject<Long> geoadd(String key, GeoAddParams params, Map<String, GeoCoordinate> memberCoordinateMap) {
    return new CommandObject<>(addGeoCoordinateFlatMapArgs(commandArguments(Command.GEOADD).key(key).addParams(params), memberCoordinateMap), BuilderFactory.LONG);
  }

  public final CommandObject<Double> geodist(String key, String member1, String member2) {
    return new CommandObject<>(commandArguments(Command.GEODIST).key(key).add(member1).add(member2), BuilderFactory.DOUBLE);
  }

  public final CommandObject<Double> geodist(String key, String member1, String member2, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEODIST).key(key).add(member1).add(member2).add(unit), BuilderFactory.DOUBLE);
  }

  public final CommandObject<List<String>> geohash(String key, String... members) {
    return new CommandObject<>(commandArguments(Command.GEOHASH).key(key).addObjects((Object[]) members), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<GeoCoordinate>> geopos(String key, String... members) {
    return new CommandObject<>(commandArguments(Command.GEOPOS).key(key).addObjects((Object[]) members),
        BuilderFactory.GEO_COORDINATE_LIST);
  }

  public final CommandObject<Long> geoadd(byte[] key, double longitude, double latitude, byte[] member) {
    return new CommandObject<>(commandArguments(Command.GEOADD).key(key).add(longitude).add(latitude).add(member), BuilderFactory.LONG);
  }

  public final CommandObject<Long> geoadd(byte[] key, Map<byte[], GeoCoordinate> memberCoordinateMap) {
    return new CommandObject<>(addGeoCoordinateFlatMapArgs(commandArguments(Command.GEOADD).key(key), memberCoordinateMap), BuilderFactory.LONG);
  }

  public final CommandObject<Long> geoadd(byte[] key, GeoAddParams params, Map<byte[], GeoCoordinate> memberCoordinateMap) {
    return new CommandObject<>(addGeoCoordinateFlatMapArgs(commandArguments(Command.GEOADD).key(key).addParams(params), memberCoordinateMap), BuilderFactory.LONG);
  }

  public final CommandObject<Double> geodist(byte[] key, byte[] member1, byte[] member2) {
    return new CommandObject<>(commandArguments(Command.GEODIST).key(key).add(member1).add(member2), BuilderFactory.DOUBLE);
  }

  public final CommandObject<Double> geodist(byte[] key, byte[] member1, byte[] member2, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEODIST).key(key).add(member1).add(member2).add(unit), BuilderFactory.DOUBLE);
  }

  public final CommandObject<List<byte[]>> geohash(byte[] key, byte[]... members) {
    return new CommandObject<>(commandArguments(Command.GEOHASH).key(key).addObjects((Object[]) members), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<GeoCoordinate>> geopos(byte[] key, byte[]... members) {
    return new CommandObject<>(commandArguments(Command.GEOPOS).key(key).addObjects((Object[]) members),
        BuilderFactory.GEO_COORDINATE_LIST);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadius(String key, double longitude, double latitude, double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEORADIUS).key(key).add(longitude).add(latitude)
        .add(radius).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadius(String key,
      double longitude, double latitude, double radius, GeoUnit unit, GeoRadiusParam param) {
    return new CommandObject<>(commandArguments(Command.GEORADIUS).key(key).add(longitude).add(latitude)
        .add(radius).add(unit).addParams(param), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadiusReadonly(String key, double longitude, double latitude, double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEORADIUS_RO).key(key).add(longitude).add(latitude)
        .add(radius).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadiusReadonly(String key,
      double longitude, double latitude, double radius, GeoUnit unit, GeoRadiusParam param) {
    return new CommandObject<>(commandArguments(Command.GEORADIUS_RO).key(key).add(longitude).add(latitude)
        .add(radius).add(unit).addParams(param), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<Long> georadiusStore(String key, double longitude, double latitude,
      double radius, GeoUnit unit, GeoRadiusParam param, GeoRadiusStoreParam storeParam) {
    return new CommandObject<>(commandArguments(Command.GEORADIUS).key(key).add(longitude).add(latitude)
        .add(radius).add(unit).addParams(param).addParams(storeParam), BuilderFactory.LONG);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadiusByMember(String key, String member, double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEORADIUSBYMEMBER).key(key).add(member)
        .add(radius).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadiusByMember(String key,
      String member, double radius, GeoUnit unit, GeoRadiusParam param) {
    return new CommandObject<>(commandArguments(Command.GEORADIUSBYMEMBER).key(key).add(member)
        .add(radius).add(unit).addParams(param), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadiusByMemberReadonly(String key, String member, double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEORADIUSBYMEMBER_RO).key(key).add(member)
        .add(radius).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadiusByMemberReadonly(String key,
      String member, double radius, GeoUnit unit, GeoRadiusParam param) {
    return new CommandObject<>(commandArguments(Command.GEORADIUSBYMEMBER_RO).key(key).add(member)
        .add(radius).add(unit).addParams(param), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<Long> georadiusByMemberStore(String key, String member,
      double radius, GeoUnit unit, GeoRadiusParam param, GeoRadiusStoreParam storeParam) {
    return new CommandObject<>(commandArguments(Command.GEORADIUSBYMEMBER).key(key).add(member)
        .add(radius).add(unit).addParams(param).addParams(storeParam), BuilderFactory.LONG);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadius(byte[] key, double longitude, double latitude, double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEORADIUS).key(key).add(longitude).add(latitude)
        .add(radius).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadius(byte[] key,
      double longitude, double latitude, double radius, GeoUnit unit, GeoRadiusParam param) {
    return new CommandObject<>(commandArguments(Command.GEORADIUS).key(key).add(longitude).add(latitude)
        .add(radius).add(unit).addParams(param), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadiusReadonly(byte[] key,
      double longitude, double latitude, double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEORADIUS_RO).key(key).add(longitude).add(latitude)
        .add(radius).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadiusReadonly(byte[] key,
      double longitude, double latitude, double radius, GeoUnit unit, GeoRadiusParam param) {
    return new CommandObject<>(commandArguments(Command.GEORADIUS_RO).key(key).add(longitude).add(latitude)
        .add(radius).add(unit).addParams(param), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<Long> georadiusStore(byte[] key, double longitude, double latitude,
      double radius, GeoUnit unit, GeoRadiusParam param, GeoRadiusStoreParam storeParam) {
    return new CommandObject<>(commandArguments(Command.GEORADIUS).key(key).add(longitude).add(latitude)
        .add(radius).add(unit).addParams(param).addParams(storeParam), BuilderFactory.LONG);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadiusByMember(byte[] key, byte[] member, double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEORADIUSBYMEMBER).key(key).add(member)
        .add(radius).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadiusByMember(byte[] key, byte[] member, double radius, GeoUnit unit, GeoRadiusParam param) {
    return new CommandObject<>(commandArguments(Command.GEORADIUSBYMEMBER).key(key).add(member)
        .add(radius).add(unit).addParams(param), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadiusByMemberReadonly(byte[] key, byte[] member, double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEORADIUSBYMEMBER_RO).key(key).add(member)
        .add(radius).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> georadiusByMemberReadonly(byte[] key, byte[] member, double radius, GeoUnit unit, GeoRadiusParam param) {
    return new CommandObject<>(commandArguments(Command.GEORADIUSBYMEMBER_RO).key(key).add(member)
        .add(radius).add(unit).addParams(param), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<Long> georadiusByMemberStore(byte[] key, byte[] member,
      double radius, GeoUnit unit, GeoRadiusParam param, GeoRadiusStoreParam storeParam) {
    return new CommandObject<>(commandArguments(Command.GEORADIUSBYMEMBER).key(key).add(member)
        .add(radius).add(unit).addParams(param).addParams(storeParam), BuilderFactory.LONG);
  }

  public final CommandObject<List<GeoRadiusResponse>> geosearch(String key, String member,
      double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCH).key(key).add(Keyword.FROMMEMBER).add(member)
        .add(Keyword.BYRADIUS).add(radius).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> geosearch(String key, GeoCoordinate coord,
      double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCH).key(key)
        .add(Keyword.FROMLONLAT).add(coord.getLongitude()).add(coord.getLatitude())
        .add(Keyword.BYRADIUS).add(radius).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> geosearch(String key, String member,
      double width, double height, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCH).key(key).add(Keyword.FROMMEMBER).add(member)
        .add(Keyword.BYBOX).add(width).add(height).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> geosearch(String key, GeoCoordinate coord,
      double width, double height, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCH).key(key)
        .add(Keyword.FROMLONLAT).add(coord.getLongitude()).add(coord.getLatitude())
        .add(Keyword.BYBOX).add(width).add(height).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> geosearch(String key, GeoSearchParam params) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCH).key(key).addParams(params),
        BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<Long> geosearchStore(String dest, String src, String member,
      double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCHSTORE).key(dest).add(src).add(Keyword.FROMMEMBER).add(member)
        .add(Keyword.BYRADIUS).add(radius).add(unit), BuilderFactory.LONG);
  }

  public final CommandObject<Long> geosearchStore(String dest, String src, GeoCoordinate coord,
      double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCHSTORE).key(dest).add(src).add(Keyword.FROMLONLAT).add(coord.getLongitude())
        .add(coord.getLatitude()).add(Keyword.BYRADIUS).add(radius).add(unit), BuilderFactory.LONG);
  }

  public final CommandObject<Long> geosearchStore(String dest, String src, String member,
      double width, double height, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCHSTORE).key(dest).add(src).add(Keyword.FROMMEMBER).add(member)
        .add(Keyword.BYBOX).add(width).add(height).add(unit), BuilderFactory.LONG);
  }

  public final CommandObject<Long> geosearchStore(String dest, String src, GeoCoordinate coord,
      double width, double height, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCHSTORE).key(dest).add(src)
        .add(Keyword.FROMLONLAT).add(coord.getLongitude()).add(coord.getLatitude())
        .add(Keyword.BYBOX).add(width).add(height).add(unit), BuilderFactory.LONG);
  }

  public final CommandObject<Long> geosearchStore(String dest, String src, GeoSearchParam params) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCHSTORE).key(dest).add(src).addParams(params), BuilderFactory.LONG);
  }

  public final CommandObject<Long> geosearchStoreStoreDist(String dest, String src, GeoSearchParam params) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCHSTORE).key(dest).add(src).addParams(params).add(
        Keyword.STOREDIST), BuilderFactory.LONG);
  }

  public final CommandObject<List<GeoRadiusResponse>> geosearch(byte[] key, byte[] member,
      double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCH).key(key).add(Keyword.FROMMEMBER).add(member)
        .add(Keyword.BYRADIUS).add(radius).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> geosearch(byte[] key, GeoCoordinate coord,
      double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCH).key(key)
        .add(Keyword.FROMLONLAT).add(coord.getLongitude()).add(coord.getLatitude())
        .add(Keyword.BYRADIUS).add(radius).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> geosearch(byte[] key, byte[] member,
      double width, double height, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCH).key(key).add(Keyword.FROMMEMBER).add(member)
        .add(Keyword.BYBOX).add(width).add(height).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> geosearch(byte[] key, GeoCoordinate coord,
      double width, double height, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCH).key(key)
        .add(Keyword.FROMLONLAT).add(coord.getLongitude()).add(coord.getLatitude())
        .add(Keyword.BYBOX).add(width).add(height).add(unit), BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<List<GeoRadiusResponse>> geosearch(byte[] key, GeoSearchParam params) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCH).key(key).addParams(params),
        BuilderFactory.GEORADIUS_WITH_PARAMS_RESULT);
  }

  public final CommandObject<Long> geosearchStore(byte[] dest, byte[] src, byte[] member,
      double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCHSTORE).key(dest).add(src).add(Keyword.FROMMEMBER).add(member)
        .add(Keyword.BYRADIUS).add(radius).add(unit), BuilderFactory.LONG);
  }

  public final CommandObject<Long> geosearchStore(byte[] dest, byte[] src, GeoCoordinate coord,
      double radius, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCHSTORE).key(dest).add(src)
        .add(Keyword.FROMLONLAT).add(coord.getLongitude()).add(coord.getLatitude())
        .add(Keyword.BYRADIUS).add(radius).add(unit), BuilderFactory.LONG);
  }

  public final CommandObject<Long> geosearchStore(byte[] dest, byte[] src, byte[] member,
      double width, double height, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCHSTORE).key(dest).add(src).add(Keyword.FROMMEMBER).add(member)
        .add(Keyword.BYBOX).add(width).add(height).add(unit), BuilderFactory.LONG);
  }

  public final CommandObject<Long> geosearchStore(byte[] dest, byte[] src, GeoCoordinate coord,
      double width, double height, GeoUnit unit) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCHSTORE).key(dest).add(src)
        .add(Keyword.FROMLONLAT).add(coord.getLongitude()).add(coord.getLatitude())
        .add(Keyword.BYBOX).add(width).add(height).add(unit), BuilderFactory.LONG);
  }

  public final CommandObject<Long> geosearchStore(byte[] dest, byte[] src, GeoSearchParam params) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCHSTORE).key(dest).add(src).addParams(params), BuilderFactory.LONG);
  }

  public final CommandObject<Long> geosearchStoreStoreDist(byte[] dest, byte[] src, GeoSearchParam params) {
    return new CommandObject<>(commandArguments(Command.GEOSEARCHSTORE).key(dest).add(src).addParams(params).add(
        Keyword.STOREDIST), BuilderFactory.LONG);
  }
  // Geo commands

  // Hyper Log Log commands
  public final CommandObject<Long> pfadd(String key, String... elements) {
    return new CommandObject<>(commandArguments(Command.PFADD).key(key).addObjects((Object[]) elements), BuilderFactory.LONG);
  }

  public final CommandObject<String> pfmerge(String destkey, String... sourcekeys) {
    return new CommandObject<>(commandArguments(Command.PFMERGE).key(destkey).keys((Object[]) sourcekeys), BuilderFactory.STRING);
  }

  public final CommandObject<Long> pfadd(byte[] key, byte[]... elements) {
    return new CommandObject<>(commandArguments(Command.PFADD).key(key).addObjects((Object[]) elements), BuilderFactory.LONG);
  }

  public final CommandObject<String> pfmerge(byte[] destkey, byte[]... sourcekeys) {
    return new CommandObject<>(commandArguments(Command.PFMERGE).key(destkey).keys((Object[]) sourcekeys), BuilderFactory.STRING);
  }

  public final CommandObject<Long> pfcount(String key) {
    return new CommandObject<>(commandArguments(Command.PFCOUNT).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> pfcount(String... keys) {
    return new CommandObject<>(commandArguments(Command.PFCOUNT).keys((Object[]) keys), BuilderFactory.LONG);
  }

  public final CommandObject<Long> pfcount(byte[] key) {
    return new CommandObject<>(commandArguments(Command.PFCOUNT).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> pfcount(byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.PFCOUNT).keys((Object[]) keys), BuilderFactory.LONG);
  }
  // Hyper Log Log commands

  // Stream commands
  public final CommandObject<StreamEntryID> xadd(String key, StreamEntryID id, Map<String, String> hash) {
    return new CommandObject<>(addFlatMapArgs(commandArguments(Command.XADD).key(key).add(id == null ? StreamEntryID.NEW_ENTRY : id), hash),
        BuilderFactory.STREAM_ENTRY_ID);
  }

  public final CommandObject<StreamEntryID> xadd(String key, XAddParams params, Map<String, String> hash) {
    return new CommandObject<>(addFlatMapArgs(commandArguments(Command.XADD).key(key).addParams(params), hash),
        BuilderFactory.STREAM_ENTRY_ID);
  }

  public final CommandObject<Long> xlen(String key) {
    return new CommandObject<>(commandArguments(Command.XLEN).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<byte[]> xadd(byte[] key, XAddParams params, Map<byte[], byte[]> hash) {
    return new CommandObject<>(addFlatMapArgs(commandArguments(Command.XADD).key(key).addParams(params), hash),
        BuilderFactory.BINARY);
  }

  public final CommandObject<Long> xlen(byte[] key) {
    return new CommandObject<>(commandArguments(Command.XLEN).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<List<StreamEntry>> xrange(String key, StreamEntryID start, StreamEntryID end) {
    return new CommandObject<>(commandArguments(Command.XRANGE).key(key).add(start == null ? "-" : start).add(end == null ? "+" : end),
        BuilderFactory.STREAM_ENTRY_LIST);
  }

  public final CommandObject<List<StreamEntry>> xrange(String key, StreamEntryID start, StreamEntryID end, int count) {
    return new CommandObject<>(commandArguments(Command.XRANGE).key(key).add(start == null ? "-" : start).add(end == null ? "+" : end)
        .add(Keyword.COUNT).add(count), BuilderFactory.STREAM_ENTRY_LIST);
  }

  public final CommandObject<List<StreamEntry>> xrevrange(String key, StreamEntryID end, StreamEntryID start) {
    return new CommandObject<>(commandArguments(Command.XREVRANGE).key(key).add(end == null ? "+" : end).add(start == null ? "-" : start),
        BuilderFactory.STREAM_ENTRY_LIST);
  }

  public final CommandObject<List<StreamEntry>> xrevrange(String key, StreamEntryID end, StreamEntryID start, int count) {
    return new CommandObject<>(commandArguments(Command.XREVRANGE).key(key).add(end == null ? "+" : end).add(start == null ? "-" : start)
        .add(Keyword.COUNT).add(count), BuilderFactory.STREAM_ENTRY_LIST);
  }

  public final CommandObject<List<StreamEntry>> xrange(String key, String start, String end) {
    return new CommandObject<>(commandArguments(Command.XRANGE).key(key).add(start).add(end), BuilderFactory.STREAM_ENTRY_LIST);
  }

  public final CommandObject<List<StreamEntry>> xrange(String key, String start, String end, int count) {
    return new CommandObject<>(commandArguments(Command.XRANGE).key(key).add(start).add(end).add(Keyword.COUNT).add(count), BuilderFactory.STREAM_ENTRY_LIST);
  }

  public final CommandObject<List<StreamEntry>> xrevrange(String key, String end, String start) {
    return new CommandObject<>(commandArguments(Command.XREVRANGE).key(key).add(end).add(start), BuilderFactory.STREAM_ENTRY_LIST);
  }

  public final CommandObject<List<StreamEntry>> xrevrange(String key, String end, String start, int count) {
    return new CommandObject<>(commandArguments(Command.XREVRANGE).key(key).add(end).add(start).add(Keyword.COUNT).add(count), BuilderFactory.STREAM_ENTRY_LIST);
  }

  public final CommandObject<List<Object>> xrange(byte[] key, byte[] start, byte[] end) {
    return new CommandObject<>(commandArguments(Command.XRANGE).key(key).add(start == null ? "-" : start).add(end == null ? "+" : end),
        BuilderFactory.RAW_OBJECT_LIST);
  }

  public final CommandObject<List<Object>> xrange(byte[] key, byte[] start, byte[] end, int count) {
    return new CommandObject<>(commandArguments(Command.XRANGE).key(key).add(start == null ? "-" : start).add(end == null ? "+" : end)
        .add(Keyword.COUNT).add(count), BuilderFactory.RAW_OBJECT_LIST);
  }

  public final CommandObject<List<Object>> xrevrange(byte[] key, byte[] end, byte[] start) {
    return new CommandObject<>(commandArguments(Command.XREVRANGE).key(key).add(end == null ? "+" : end).add(start == null ? "-" : start),
        BuilderFactory.RAW_OBJECT_LIST);
  }

  public final CommandObject<List<Object>> xrevrange(byte[] key, byte[] end, byte[] start, int count) {
    return new CommandObject<>(commandArguments(Command.XREVRANGE).key(key).add(end == null ? "+" : end).add(start == null ? "-" : start)
        .add(Keyword.COUNT).add(count), BuilderFactory.RAW_OBJECT_LIST);
  }

  public final CommandObject<Long> xack(String key, String group, StreamEntryID... ids) {
    return new CommandObject<>(commandArguments(Command.XACK).key(key).add(group).addObjects((Object[]) ids), BuilderFactory.LONG);
  }

  public final CommandObject<Long> xack(byte[] key, byte[] group, byte[]... ids) {
    return new CommandObject<>(commandArguments(Command.XACK).key(key).add(group).addObjects((Object[]) ids), BuilderFactory.LONG);
  }

  public final CommandObject<String> xgroupCreate(String key, String groupName, StreamEntryID id, boolean makeStream) {
    CommandArguments args = commandArguments(Command.XGROUP).add(Keyword.CREATE).key(key)
        .add(groupName).add(id == null ? "0-0" : id);
    if (makeStream) args.add(Keyword.MKSTREAM);
    return new CommandObject<>(args, BuilderFactory.STRING);
  }

  public final CommandObject<String> xgroupSetID(String key, String groupName, StreamEntryID id) {
    return new CommandObject<>(commandArguments(Command.XGROUP).add(Keyword.SETID)
        .key(key).add(groupName).add(id), BuilderFactory.STRING);
  }

  public final CommandObject<Long> xgroupDestroy(String key, String groupName) {
    return new CommandObject<>(commandArguments(Command.XGROUP).add(Keyword.DESTROY)
        .key(key).add(groupName), BuilderFactory.LONG);
  }

  public final CommandObject<Boolean> xgroupCreateConsumer(String key, String groupName, String consumerName) {
    return new CommandObject<>(commandArguments(Command.XGROUP).add(Keyword.CREATECONSUMER)
        .key(key).add(groupName).add(consumerName), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Long> xgroupDelConsumer(String key, String groupName, String consumerName) {
    return new CommandObject<>(commandArguments(Command.XGROUP).add(Keyword.DELCONSUMER)
        .key(key).add(groupName).add(consumerName), BuilderFactory.LONG);
  }

  public final CommandObject<String> xgroupCreate(byte[] key, byte[] groupName, byte[] id, boolean makeStream) {
    CommandArguments args = commandArguments(Command.XGROUP).add(Keyword.CREATE).key(key)
        .add(groupName).add(id);
    if (makeStream) args.add(Keyword.MKSTREAM);
    return new CommandObject<>(args, BuilderFactory.STRING);
  }

  public final CommandObject<String> xgroupSetID(byte[] key, byte[] groupName, byte[] id) {
    return new CommandObject<>(commandArguments(Command.XGROUP).add(Keyword.SETID)
        .key(key).add(groupName).add(id), BuilderFactory.STRING);
  }

  public final CommandObject<Long> xgroupDestroy(byte[] key, byte[] groupName) {
    return new CommandObject<>(commandArguments(Command.XGROUP).add(Keyword.DESTROY)
        .key(key).add(groupName), BuilderFactory.LONG);
  }

  public final CommandObject<Boolean> xgroupCreateConsumer(byte[] key, byte[] groupName, byte[] consumerName) {
    return new CommandObject<>(commandArguments(Command.XGROUP).add(Keyword.CREATECONSUMER)
        .key(key).add(groupName).add(consumerName), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Long> xgroupDelConsumer(byte[] key, byte[] groupName, byte[] consumerName) {
    return new CommandObject<>(commandArguments(Command.XGROUP).add(Keyword.DELCONSUMER)
        .key(key).add(groupName).add(consumerName), BuilderFactory.LONG);
  }
  public final CommandObject<Long> xdel(String key, StreamEntryID... ids) {
    return new CommandObject<>(commandArguments(Command.XDEL).key(key).addObjects((Object[]) ids), BuilderFactory.LONG);
  }

  public final CommandObject<Long> xtrim(String key, long maxLen, boolean approximate) {
    CommandArguments args = commandArguments(Command.XTRIM).key(key).add(Keyword.MAXLEN);
    if (approximate) args.add(Protocol.BYTES_TILDE);
    args.add(maxLen);
    return new CommandObject<>(args, BuilderFactory.LONG);
  }

  public final CommandObject<Long> xtrim(String key, XTrimParams params) {
    return new CommandObject<>(commandArguments(Command.XTRIM).key(key).addParams(params), BuilderFactory.LONG);
  }

  public final CommandObject<Long> xdel(byte[] key, byte[]... ids) {
    return new CommandObject<>(commandArguments(Command.XDEL).key(key).addObjects((Object[]) ids), BuilderFactory.LONG);
  }

  public final CommandObject<Long> xtrim(byte[] key, long maxLen, boolean approximateLength) {
    CommandArguments args = commandArguments(Command.XTRIM).key(key).add(Keyword.MAXLEN);
    if (approximateLength) args.add(Protocol.BYTES_TILDE);
    args.add(maxLen);
    return new CommandObject<>(args, BuilderFactory.LONG);
  }

  public final CommandObject<Long> xtrim(byte[] key, XTrimParams params) {
    return new CommandObject<>(commandArguments(Command.XTRIM).key(key).addParams(params), BuilderFactory.LONG);
  }

  public final CommandObject<StreamPendingSummary> xpending(String key, String groupName) {
    return new CommandObject<>(commandArguments(Command.XPENDING).key(key).add(groupName),
        BuilderFactory.STREAM_PENDING_SUMMARY);
  }

  public final CommandObject<List<StreamPendingEntry>> xpending(String key, String groupName, XPendingParams params) {
    return new CommandObject<>(commandArguments(Command.XPENDING).key(key).add(groupName)
        .addParams(params), BuilderFactory.STREAM_PENDING_ENTRY_LIST);
  }

  public final CommandObject<Object> xpending(byte[] key, byte[] groupName) {
    return new CommandObject<>(commandArguments(Command.XPENDING).key(key).add(groupName),
        BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<List<Object>> xpending(byte[] key, byte[] groupName, XPendingParams params) {
    return new CommandObject<>(commandArguments(Command.XPENDING).key(key).add(groupName)
        .addParams(params), BuilderFactory.RAW_OBJECT_LIST);
  }

  public final CommandObject<List<StreamEntry>> xclaim(String key, String group,
      String consumerName, long minIdleTime, XClaimParams params, StreamEntryID... ids) {
    return new CommandObject<>(commandArguments(Command.XCLAIM).key(key).add(group)
        .add(consumerName).add(minIdleTime).addObjects((Object[]) ids).addParams(params),
        BuilderFactory.STREAM_ENTRY_LIST);
  }

  public final CommandObject<List<StreamEntryID>> xclaimJustId(String key, String group,
      String consumerName, long minIdleTime, XClaimParams params, StreamEntryID... ids) {
    return new CommandObject<>(commandArguments(Command.XCLAIM).key(key).add(group)
        .add(consumerName).add(minIdleTime).addObjects((Object[]) ids).addParams(params)
        .add(Keyword.JUSTID), BuilderFactory.STREAM_ENTRY_ID_LIST);
  }

  public final CommandObject<Map.Entry<StreamEntryID, List<StreamEntry>>> xautoclaim(String key,
      String group, String consumerName, long minIdleTime, StreamEntryID start,
      XAutoClaimParams params) {
    return new CommandObject<>(commandArguments(Command.XAUTOCLAIM).key(key).add(group)
        .add(consumerName).add(minIdleTime).add(start).addParams(params),
        BuilderFactory.STREAM_AUTO_CLAIM_RESPONSE);
  }

  public final CommandObject<Map.Entry<StreamEntryID, List<StreamEntryID>>> xautoclaimJustId(
      String key, String group, String consumerName, long minIdleTime, StreamEntryID start,
      XAutoClaimParams params) {
    return new CommandObject<>(commandArguments(Command.XAUTOCLAIM).key(key).add(group)
        .add(consumerName).add(minIdleTime).add(start).addParams(params)
        .add(Keyword.JUSTID), BuilderFactory.STREAM_AUTO_CLAIM_JUSTID_RESPONSE);
  }

  public final CommandObject<List<byte[]>> xclaim(byte[] key, byte[] group,
      byte[] consumerName, long minIdleTime, XClaimParams params, byte[]... ids) {
    return new CommandObject<>(commandArguments(Command.XCLAIM).key(key).add(group)
        .add(consumerName).add(minIdleTime).addObjects((Object[]) ids).addParams(params),
        BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<byte[]>> xclaimJustId(byte[] key, byte[] group,
      byte[] consumerName, long minIdleTime, XClaimParams params, byte[]... ids) {
    return new CommandObject<>(commandArguments(Command.XCLAIM).key(key).add(group)
        .add(consumerName).add(minIdleTime).addObjects((Object[]) ids).addParams(params)
        .add(Keyword.JUSTID), BuilderFactory.BINARY_LIST);
  }

  public final CommandObject<List<Object>> xautoclaim(byte[] key, byte[] groupName,
      byte[] consumerName, long minIdleTime, byte[] start, XAutoClaimParams params) {
    return new CommandObject<>(commandArguments(Command.XAUTOCLAIM).key(key).add(groupName)
        .add(consumerName).add(minIdleTime).add(start).addParams(params),
        BuilderFactory.RAW_OBJECT_LIST);
  }

  public final CommandObject<List<Object>> xautoclaimJustId(byte[] key, byte[] groupName,
      byte[] consumerName, long minIdleTime, byte[] start, XAutoClaimParams params) {
    return new CommandObject<>(commandArguments(Command.XAUTOCLAIM).key(key).add(groupName)
        .add(consumerName).add(minIdleTime).add(start).addParams(params)
        .add(Keyword.JUSTID), BuilderFactory.RAW_OBJECT_LIST);
  }

  public final CommandObject<StreamInfo> xinfoStream(String key) {
    return new CommandObject<>(commandArguments(Command.XINFO).add(Keyword.STREAM).key(key), BuilderFactory.STREAM_INFO);
  }

  public final CommandObject<Object> xinfoStream(byte[] key) {
    return new CommandObject<>(commandArguments(Command.XINFO).add(Keyword.STREAM).key(key), BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<StreamFullInfo> xinfoStreamFull(String key) {
    return new CommandObject<>(commandArguments(Command.XINFO).add(Keyword.STREAM).key(key).add(Keyword.FULL), BuilderFactory.STREAM_FULL_INFO);
  }

  public final CommandObject<StreamFullInfo> xinfoStreamFull(String key, int count) {
    return new CommandObject<>(commandArguments(Command.XINFO).add(Keyword.STREAM).key(key).add(Keyword.FULL).add(
        Keyword.COUNT).add(count), BuilderFactory.STREAM_FULL_INFO);
  }

  public final CommandObject<Object> xinfoStreamFull(byte[] key, int count) {
    return new CommandObject<>(commandArguments(Command.XINFO).add(Keyword.STREAM).key(key).add(Keyword.FULL).add(
        Keyword.COUNT).add(count), BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<Object> xinfoStreamFull(byte[] key) {
    return new CommandObject<>(commandArguments(Command.XINFO).add(Keyword.STREAM).key(key).add(Keyword.FULL), BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<List<StreamGroupInfo>> xinfoGroups(String key) {
    return new CommandObject<>(commandArguments(Command.XINFO).add(Keyword.GROUPS).key(key), BuilderFactory.STREAM_GROUP_INFO_LIST);
  }

  public final CommandObject<List<Object>> xinfoGroups(byte[] key) {
    return new CommandObject<>(commandArguments(Command.XINFO).add(Keyword.GROUPS).key(key), BuilderFactory.RAW_OBJECT_LIST);
  }

  /**
   * @deprecated Use {@link #xinfoConsumers2(java.lang.String, java.lang.String)}.
   */
  @Deprecated
  public final CommandObject<List<StreamConsumersInfo>> xinfoConsumers(String key, String group) {
    return new CommandObject<>(commandArguments(Command.XINFO).add(Keyword.CONSUMERS).key(key).add(group), BuilderFactory.STREAM_CONSUMERS_INFO_LIST);
  }

  public final CommandObject<List<StreamConsumerInfo>> xinfoConsumers2(String key, String group) {
    return new CommandObject<>(commandArguments(Command.XINFO).add(Keyword.CONSUMERS).key(key).add(group), BuilderFactory.STREAM_CONSUMER_INFO_LIST);
  }

  public final CommandObject<List<Object>> xinfoConsumers(byte[] key, byte[] group) {
    return new CommandObject<>(commandArguments(Command.XINFO).add(Keyword.CONSUMERS).key(key).add(group), BuilderFactory.RAW_OBJECT_LIST);
  }

  public final CommandObject<List<Map.Entry<String, List<StreamEntry>>>> xread(
      XReadParams xReadParams, Map<String, StreamEntryID> streams) {
    CommandArguments args = commandArguments(Command.XREAD).addParams(xReadParams).add(Keyword.STREAMS);
    Set<Map.Entry<String, StreamEntryID>> entrySet = streams.entrySet();
    entrySet.forEach(entry -> args.key(entry.getKey()));
    entrySet.forEach(entry -> args.add(entry.getValue()));
    return new CommandObject<>(args, BuilderFactory.STREAM_READ_RESPONSE);
  }

  public final CommandObject<Map<String, List<StreamEntry>>> xreadAsMap(
      XReadParams xReadParams, Map<String, StreamEntryID> streams) {
    CommandArguments args = commandArguments(Command.XREAD).addParams(xReadParams).add(Keyword.STREAMS);
    Set<Map.Entry<String, StreamEntryID>> entrySet = streams.entrySet();
    entrySet.forEach(entry -> args.key(entry.getKey()));
    entrySet.forEach(entry -> args.add(entry.getValue()));
    return new CommandObject<>(args, BuilderFactory.STREAM_READ_MAP_RESPONSE);
  }

  public final CommandObject<List<Map.Entry<String, List<StreamEntry>>>> xreadGroup(
      String groupName, String consumer, XReadGroupParams xReadGroupParams,
      Map<String, StreamEntryID> streams) {
    CommandArguments args = commandArguments(Command.XREADGROUP)
        .add(Keyword.GROUP).add(groupName).add(consumer)
        .addParams(xReadGroupParams).add(Keyword.STREAMS);
    Set<Map.Entry<String, StreamEntryID>> entrySet = streams.entrySet();
    entrySet.forEach(entry -> args.key(entry.getKey()));
    entrySet.forEach(entry -> args.add(entry.getValue()));
    return new CommandObject<>(args, BuilderFactory.STREAM_READ_RESPONSE);
  }

  public final CommandObject<Map<String, List<StreamEntry>>> xreadGroupAsMap(
      String groupName, String consumer, XReadGroupParams xReadGroupParams,
      Map<String, StreamEntryID> streams) {
    CommandArguments args = commandArguments(Command.XREADGROUP)
        .add(Keyword.GROUP).add(groupName).add(consumer)
        .addParams(xReadGroupParams).add(Keyword.STREAMS);
    Set<Map.Entry<String, StreamEntryID>> entrySet = streams.entrySet();
    entrySet.forEach(entry -> args.key(entry.getKey()));
    entrySet.forEach(entry -> args.add(entry.getValue()));
    return new CommandObject<>(args, BuilderFactory.STREAM_READ_MAP_RESPONSE);
  }

  public final CommandObject<List<Object>> xread(XReadParams xReadParams, Map.Entry<byte[], byte[]>... streams) {
    CommandArguments args = commandArguments(Command.XREAD).addParams(xReadParams).add(Keyword.STREAMS);
    for (Map.Entry<byte[], byte[]> entry : streams) {
      args.key(entry.getKey());
    }
    for (Map.Entry<byte[], byte[]> entry : streams) {
      args.add(entry.getValue());
    }
    return new CommandObject<>(args, BuilderFactory.RAW_OBJECT_LIST);
  }

  public final CommandObject<List<Object>> xreadGroup(byte[] groupName, byte[] consumer,
      XReadGroupParams xReadGroupParams, Map.Entry<byte[], byte[]>... streams) {
    CommandArguments args = commandArguments(Command.XREADGROUP)
        .add(Keyword.GROUP).add(groupName).add(consumer)
        .addParams(xReadGroupParams).add(Keyword.STREAMS);
    for (Map.Entry<byte[], byte[]> entry : streams) {
      args.key(entry.getKey());
    }
    for (Map.Entry<byte[], byte[]> entry : streams) {
      args.add(entry.getValue());
    }
    return new CommandObject<>(args, BuilderFactory.RAW_OBJECT_LIST);
  }
  // Stream commands

  // Scripting commands
  public final CommandObject<Object> eval(String script) {
    return new CommandObject<>(commandArguments(Command.EVAL).add(script).add(0), BuilderFactory.AGGRESSIVE_ENCODED_OBJECT);
  }

  public final CommandObject<Object> eval(String script, String sampleKey) {
    return new CommandObject<>(commandArguments(Command.EVAL).add(script).add(0).processKey(sampleKey), BuilderFactory.AGGRESSIVE_ENCODED_OBJECT);
  }

  public final CommandObject<Object> eval(String script, int keyCount, String... params) {
    return new CommandObject<>(commandArguments(Command.EVAL).add(script).add(keyCount)
        .addObjects((Object[]) params).processKeys(Arrays.copyOf(params, keyCount)),
        BuilderFactory.AGGRESSIVE_ENCODED_OBJECT);
  }

  public final CommandObject<Object> eval(String script, List<String> keys, List<String> args) {
    return new CommandObject<>(commandArguments(Command.EVAL).add(script).add(keys.size())
        .keys(keys).addObjects(args), BuilderFactory.AGGRESSIVE_ENCODED_OBJECT);
  }

  public final CommandObject<Object> evalReadonly(String script, List<String> keys, List<String> args) {
    return new CommandObject<>(commandArguments(Command.EVAL_RO).add(script).add(keys.size())
        .keys(keys).addObjects(args), BuilderFactory.AGGRESSIVE_ENCODED_OBJECT);
  }

  public final CommandObject<Object> eval(byte[] script) {
    return new CommandObject<>(commandArguments(Command.EVAL).add(script).add(0), BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<Object> eval(byte[] script, byte[] sampleKey) {
    return new CommandObject<>(commandArguments(Command.EVAL).add(script).add(0).processKey(sampleKey), BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<Object> eval(byte[] script, int keyCount, byte[]... params) {
    return new CommandObject<>(commandArguments(Command.EVAL).add(script).add(keyCount)
        .addObjects((Object[]) params).processKeys(Arrays.copyOf(params, keyCount)),
        BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<Object> eval(byte[] script, List<byte[]> keys, List<byte[]> args) {
    return new CommandObject<>(commandArguments(Command.EVAL).add(script).add(keys.size())
        .keys(keys).addObjects(args), BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<Object> evalReadonly(byte[] script, List<byte[]> keys, List<byte[]> args) {
    return new CommandObject<>(commandArguments(Command.EVAL_RO).add(script).add(keys.size())
        .keys(keys).addObjects(args), BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<Object> evalsha(String sha1) {
    return new CommandObject<>(commandArguments(Command.EVALSHA).add(sha1).add(0), BuilderFactory.AGGRESSIVE_ENCODED_OBJECT);
  }

  public final CommandObject<Object> evalsha(String sha1, String sampleKey) {
    return new CommandObject<>(commandArguments(Command.EVALSHA).add(sha1).add(0).processKey(sampleKey), BuilderFactory.AGGRESSIVE_ENCODED_OBJECT);
  }

  public final CommandObject<Object> evalsha(String sha1, int keyCount, String... params) {
    return new CommandObject<>(commandArguments(Command.EVALSHA).add(sha1).add(keyCount)
        .addObjects((Object[]) params).processKeys(Arrays.copyOf(params, keyCount)),
        BuilderFactory.AGGRESSIVE_ENCODED_OBJECT);
  }

  public final CommandObject<Object> evalsha(String sha1, List<String> keys, List<String> args) {
    return new CommandObject<>(commandArguments(Command.EVALSHA).add(sha1).add(keys.size())
        .keys(keys).addObjects(args), BuilderFactory.AGGRESSIVE_ENCODED_OBJECT);
  }

  public final CommandObject<Object> evalshaReadonly(String sha1, List<String> keys, List<String> args) {
    return new CommandObject<>(commandArguments(Command.EVALSHA_RO).add(sha1).add(keys.size())
        .keys(keys).addObjects(args), BuilderFactory.AGGRESSIVE_ENCODED_OBJECT);
  }

  public final CommandObject<Object> evalsha(byte[] sha1) {
    return new CommandObject<>(commandArguments(Command.EVALSHA).add(sha1).add(0), BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<Object> evalsha(byte[] sha1, byte[] sampleKey) {
    return new CommandObject<>(commandArguments(Command.EVALSHA).add(sha1).add(0).processKey(sampleKey), BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<Object> evalsha(byte[] sha1, int keyCount, byte[]... params) {
    return new CommandObject<>(commandArguments(Command.EVALSHA).add(sha1).add(keyCount)
        .addObjects((Object[]) params).processKeys(Arrays.copyOf(params, keyCount)),
        BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<Object> evalsha(byte[] sha1, List<byte[]> keys, List<byte[]> args) {
    return new CommandObject<>(commandArguments(Command.EVALSHA).add(sha1).add(keys.size())
        .keys(keys).addObjects(args), BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<Object> evalshaReadonly(byte[] sha1, List<byte[]> keys, List<byte[]> args) {
    return new CommandObject<>(commandArguments(Command.EVALSHA_RO).add(sha1).add(keys.size())
        .keys(keys).addObjects(args), BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<List<Boolean>> scriptExists(List<String> sha1s) {
    return new CommandObject<>(commandArguments(Command.SCRIPT).add(Keyword.EXISTS).addObjects(sha1s), BuilderFactory.BOOLEAN_LIST);
  }

  public final CommandObject<List<Boolean>> scriptExists(String sampleKey, String... sha1s) {
    return new CommandObject<>(commandArguments(Command.SCRIPT).add(Keyword.EXISTS).addObjects((Object[]) sha1s)
        .processKey(sampleKey), BuilderFactory.BOOLEAN_LIST);
  }

  public final CommandObject<String> scriptLoad(String script) {
    return new CommandObject<>(commandArguments(Command.SCRIPT).add(Keyword.LOAD).add(script), BuilderFactory.STRING);
  }

  public final CommandObject<String> scriptLoad(String script, String sampleKey) {
    return new CommandObject<>(commandArguments(Command.SCRIPT).add(Keyword.LOAD).add(script).processKey(sampleKey), BuilderFactory.STRING);
  }

  private final CommandObject<String> SCRIPT_FLUSH_COMMAND_OBJECT = new CommandObject<>(commandArguments(Command.SCRIPT).add(
      Keyword.FLUSH), BuilderFactory.STRING);

  public final CommandObject<String> scriptFlush() {
    return SCRIPT_FLUSH_COMMAND_OBJECT;
  }

  public final CommandObject<String> scriptFlush(String sampleKey) {
    return new CommandObject<>(commandArguments(Command.SCRIPT).add(Keyword.FLUSH).processKey(sampleKey), BuilderFactory.STRING);
  }

  public final CommandObject<String> scriptFlush(String sampleKey, FlushMode flushMode) {
    return new CommandObject<>(commandArguments(Command.SCRIPT).add(Keyword.FLUSH).add(flushMode).processKey(sampleKey), BuilderFactory.STRING);
  }

  private final CommandObject<String> SCRIPT_KILL_COMMAND_OBJECT = new CommandObject<>(commandArguments(Command.SCRIPT).add(
      Keyword.KILL), BuilderFactory.STRING);

  public final CommandObject<String> scriptKill() {
    return SCRIPT_KILL_COMMAND_OBJECT;
  }

  public final CommandObject<String> scriptKill(String sampleKey) {
    return new CommandObject<>(commandArguments(Command.SCRIPT).add(Keyword.KILL).processKey(sampleKey), BuilderFactory.STRING);
  }

  public final CommandObject<List<Boolean>> scriptExists(byte[] sampleKey, byte[]... sha1s) {
    return new CommandObject<>(commandArguments(Command.SCRIPT).add(Keyword.EXISTS).addObjects((Object[]) sha1s)
        .processKey(sampleKey), BuilderFactory.BOOLEAN_LIST);
  }

  public final CommandObject<byte[]> scriptLoad(byte[] script, byte[] sampleKey) {
    return new CommandObject<>(commandArguments(Command.SCRIPT).add(Keyword.LOAD).add(script).processKey(sampleKey), BuilderFactory.BINARY);
  }

  public final CommandObject<String> scriptFlush(byte[] sampleKey) {
    return new CommandObject<>(commandArguments(Command.SCRIPT).add(Keyword.FLUSH).processKey(sampleKey), BuilderFactory.STRING);
  }

  public final CommandObject<String> scriptFlush(byte[] sampleKey, FlushMode flushMode) {
    return new CommandObject<>(commandArguments(Command.SCRIPT).add(Keyword.FLUSH).add(flushMode).processKey(sampleKey), BuilderFactory.STRING);
  }

  public final CommandObject<String> scriptKill(byte[] sampleKey) {
    return new CommandObject<>(commandArguments(Command.SCRIPT).add(Keyword.KILL).processKey(sampleKey), BuilderFactory.STRING);
  }

  private final CommandObject<String> SLOWLOG_RESET_COMMAND_OBJECT
      = new CommandObject<>(commandArguments(Command.SLOWLOG).add(Keyword.RESET), BuilderFactory.STRING);

  public final CommandObject<String> slowlogReset() {
    return SLOWLOG_RESET_COMMAND_OBJECT;
  }

  public final CommandObject<Object> fcall(String name, List<String> keys, List<String> args) {
    return new CommandObject<>(commandArguments(Command.FCALL).add(name).add(keys.size())
        .keys(keys).addObjects(args), BuilderFactory.AGGRESSIVE_ENCODED_OBJECT);
  }

  public final CommandObject<Object> fcallReadonly(String name, List<String> keys, List<String> args) {
    return new CommandObject<>(commandArguments(Command.FCALL_RO).add(name).add(keys.size())
        .keys(keys).addObjects(args), BuilderFactory.AGGRESSIVE_ENCODED_OBJECT);
  }

  public final CommandObject<String> functionDelete(String libraryName) {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.DELETE).add(libraryName), BuilderFactory.STRING);
  }

  public final CommandObject<List<LibraryInfo>> functionList() {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.LIST), LibraryInfo.LIBRARY_INFO_LIST);
  }

  public final CommandObject<List<LibraryInfo>> functionList(String libraryNamePattern) {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.LIST).add(Keyword.LIBRARYNAME)
        .add(libraryNamePattern), LibraryInfo.LIBRARY_INFO_LIST);
  }

  public final CommandObject<List<LibraryInfo>> functionListWithCode() {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.LIST).add(Keyword.WITHCODE), LibraryInfo.LIBRARY_INFO_LIST);
  }

  public final CommandObject<List<LibraryInfo>> functionListWithCode(String libraryNamePattern) {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.LIST).add(Keyword.LIBRARYNAME)
        .add(libraryNamePattern).add(Keyword.WITHCODE), LibraryInfo.LIBRARY_INFO_LIST);
  }

  public final CommandObject<String> functionLoad(String functionCode) {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.LOAD).add(functionCode), BuilderFactory.STRING);
  }

  public final CommandObject<String> functionLoadReplace(String functionCode) {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.LOAD).add(Keyword.REPLACE).add(functionCode), BuilderFactory.STRING);
  }

  public final CommandObject<FunctionStats> functionStats() {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.STATS), FunctionStats.FUNCTION_STATS_BUILDER);
  }

  public final CommandObject<Object> functionStatsBinary() {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.STATS), BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<String> functionFlush() {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.FLUSH), BuilderFactory.STRING);
  }

  public final CommandObject<String> functionFlush(FlushMode mode) {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.FLUSH).add(mode), BuilderFactory.STRING);
  }

  public final CommandObject<String> functionKill() {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.KILL), BuilderFactory.STRING);
  }

  public final CommandObject<Object> fcall(byte[] name, List<byte[]> keys, List<byte[]> args) {
    return new CommandObject<>(commandArguments(Command.FCALL).add(name).add(keys.size())
        .keys(keys).addObjects(args), BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<Object> fcallReadonly(byte[] name, List<byte[]> keys, List<byte[]> args) {
    return new CommandObject<>(commandArguments(Command.FCALL_RO).add(name).add(keys.size())
        .keys(keys).addObjects(args), BuilderFactory.RAW_OBJECT);
  }

  public final CommandObject<String> functionDelete(byte[] libraryName) {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.DELETE).add(libraryName), BuilderFactory.STRING);
  }

  public final CommandObject<byte[]> functionDump() {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.DUMP), BuilderFactory.BINARY);
  }

  public final CommandObject<List<Object>> functionListBinary() {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.LIST), BuilderFactory.RAW_OBJECT_LIST);
  }

  public final CommandObject<List<Object>> functionList(byte[] libraryNamePattern) {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.LIST).add(Keyword.LIBRARYNAME)
        .add(libraryNamePattern), BuilderFactory.RAW_OBJECT_LIST);
  }

  public final CommandObject<List<Object>> functionListWithCodeBinary() {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.LIST).add(Keyword.WITHCODE), BuilderFactory.RAW_OBJECT_LIST);
  }

  public final CommandObject<List<Object>> functionListWithCode(byte[] libraryNamePattern) {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.LIST).add(Keyword.LIBRARYNAME).
        add(libraryNamePattern).add(Keyword.WITHCODE), BuilderFactory.RAW_OBJECT_LIST);
  }

  public final CommandObject<String> functionLoad(byte[] functionCode) {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.LOAD).add(functionCode), BuilderFactory.STRING);
  }

  public final CommandObject<String> functionLoadReplace(byte[] functionCode) {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Keyword.LOAD).add(Keyword.REPLACE).add(functionCode), BuilderFactory.STRING);
  }

  public final CommandObject<String> functionRestore(byte[] serializedValue) {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Command.RESTORE).add(serializedValue),
        BuilderFactory.STRING);
  }

  public final CommandObject<String> functionRestore(byte[] serializedValue, FunctionRestorePolicy policy) {
    return new CommandObject<>(commandArguments(Command.FUNCTION).add(Command.RESTORE).add(serializedValue)
        .add(policy.getRaw()), BuilderFactory.STRING);
  }
  // Scripting commands

  // Miscellaneous commands
  public final CommandObject<Boolean> copy(String srcKey, String dstKey, int dstDB, boolean replace) {
    CommandArguments args = commandArguments(Command.COPY).key(srcKey).key(dstKey).add(Keyword.DB).add(dstDB);
    if (replace) args.add(Keyword.REPLACE);
    return new CommandObject<>(args, BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Boolean> copy(byte[] srcKey, byte[] dstKey, int dstDB, boolean replace) {
    CommandArguments args = commandArguments(Command.COPY).key(srcKey).key(dstKey).add(Keyword.DB).add(dstDB);
    if (replace) args.add(Keyword.REPLACE);
    return new CommandObject<>(args, BuilderFactory.BOOLEAN);
  }

  public final CommandObject<String> migrate(String host, int port, String key, int timeout) {
    return migrate(host, port, key, 0, timeout);
  }

  public final CommandObject<String> migrate(String host, int port, String key, int destinationDB, int timeout) {
    return new CommandObject<>(commandArguments(Command.MIGRATE).add(host).add(port).key(key)
        .add(destinationDB).add(timeout), BuilderFactory.STRING);
  }

  public final CommandObject<String> migrate(String host, int port, int timeout, MigrateParams params, String... keys) {
    return migrate(host, port, 0, timeout, params, keys);
  }

  public final CommandObject<String> migrate(String host, int port, int destinationDB, int timeout,
      MigrateParams params, String... keys) {
    return new CommandObject<>(commandArguments(Command.MIGRATE).add(host).add(port).add(new byte[0])
        .add(destinationDB).add(timeout).addParams(params).add(Keyword.KEYS).keys((Object[]) keys),
        BuilderFactory.STRING);
  }

  public final CommandObject<String> migrate(String host, int port, byte[] key, int timeout) {
    return migrate(host, port, key, 0, timeout);
  }

  public final CommandObject<String> migrate(String host, int port, byte[] key, int destinationDB, int timeout) {
    return new CommandObject<>(commandArguments(Command.MIGRATE).add(host).add(port).key(key)
        .add(destinationDB).add(timeout), BuilderFactory.STRING);
  }

  public final CommandObject<String> migrate(String host, int port, int timeout, MigrateParams params, byte[]... keys) {
    return migrate(host, port, 0, timeout, params, keys);
  }

  public final CommandObject<String> migrate(String host, int port, int destinationDB, int timeout,
      MigrateParams params, byte[]... keys) {
    return new CommandObject<>(commandArguments(Command.MIGRATE).add(host).add(port).add(new byte[0])
        .add(destinationDB).add(timeout).addParams(params).add(Keyword.KEYS).keys((Object[]) keys),
        BuilderFactory.STRING);
  }

  public final CommandObject<Long> memoryUsage(String key) {
    return new CommandObject<>(commandArguments(Command.MEMORY).add(Keyword.USAGE).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> memoryUsage(String key, int samples) {
    return new CommandObject<>(commandArguments(Command.MEMORY).add(Keyword.USAGE).key(key).add(Keyword.SAMPLES).add(samples), BuilderFactory.LONG);
  }

  public final CommandObject<Long> memoryUsage(byte[] key) {
    return new CommandObject<>(commandArguments(Command.MEMORY).add(Keyword.USAGE).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> memoryUsage(byte[] key, int samples) {
    return new CommandObject<>(commandArguments(Command.MEMORY).add(Keyword.USAGE).key(key).add(Keyword.SAMPLES).add(samples), BuilderFactory.LONG);
  }

  public final CommandObject<Long> objectRefcount(String key) {
    return new CommandObject<>(commandArguments(Command.OBJECT).add(Keyword.REFCOUNT).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<String> objectEncoding(String key) {
    return new CommandObject<>(commandArguments(Command.OBJECT).add(Keyword.ENCODING).key(key), BuilderFactory.STRING);
  }

  public final CommandObject<Long> objectIdletime(String key) {
    return new CommandObject<>(commandArguments(Command.OBJECT).add(Keyword.IDLETIME).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> objectFreq(String key) {
    return new CommandObject<>(commandArguments(Command.OBJECT).add(Keyword.FREQ).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> objectRefcount(byte[] key) {
    return new CommandObject<>(commandArguments(Command.OBJECT).add(Keyword.REFCOUNT).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<byte[]> objectEncoding(byte[] key) {
    return new CommandObject<>(commandArguments(Command.OBJECT).add(Keyword.ENCODING).key(key), BuilderFactory.BINARY);
  }

  public final CommandObject<Long> objectIdletime(byte[] key) {
    return new CommandObject<>(commandArguments(Command.OBJECT).add(Keyword.IDLETIME).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> objectFreq(byte[] key) {
    return new CommandObject<>(commandArguments(Command.OBJECT).add(Keyword.FREQ).key(key), BuilderFactory.LONG);
  }

  public CommandObject<Long> waitReplicas(int replicas, long timeout) {
    return new CommandObject<>(commandArguments(Command.WAIT).add(replicas).add(timeout), BuilderFactory.LONG);
  }

  public final CommandObject<Long> waitReplicas(String sampleKey, int replicas, long timeout) {
    return new CommandObject<>(commandArguments(Command.WAIT).add(replicas).add(timeout).processKey(sampleKey), BuilderFactory.LONG);
  }

  public final CommandObject<Long> waitReplicas(byte[] sampleKey, int replicas, long timeout) {
    return new CommandObject<>(commandArguments(Command.WAIT).add(replicas).add(timeout).processKey(sampleKey), BuilderFactory.LONG);
  }

  public CommandObject<KeyValue<Long, Long>> waitAOF(long numLocal, long numReplicas, long timeout) {
    return new CommandObject<>(commandArguments(Command.WAITAOF).add(numLocal).add(numReplicas).add(timeout), BuilderFactory.LONG_LONG_PAIR);
  }

  public CommandObject<KeyValue<Long, Long>> waitAOF(byte[] sampleKey, long numLocal, long numReplicas, long timeout) {
    return new CommandObject<>(commandArguments(Command.WAITAOF).add(numLocal).add(numReplicas).add(timeout).processKey(sampleKey), BuilderFactory.LONG_LONG_PAIR);
  }

  public CommandObject<KeyValue<Long, Long>> waitAOF(String sampleKey, long numLocal, long numReplicas, long timeout) {
    return new CommandObject<>(commandArguments(Command.WAITAOF).add(numLocal).add(numReplicas).add(timeout).processKey(sampleKey), BuilderFactory.LONG_LONG_PAIR);
  }

  public final CommandObject<Long> publish(String channel, String message) {
    return new CommandObject<>(commandArguments(Command.PUBLISH).add(channel).add(message), BuilderFactory.LONG);
  }

  public final CommandObject<Long> publish(byte[] channel, byte[] message) {
    return new CommandObject<>(commandArguments(Command.PUBLISH).add(channel).add(message), BuilderFactory.LONG);
  }

  public final CommandObject<Long> spublish(String channel, String message) {
    return new CommandObject<>(commandArguments(Command.SPUBLISH).key(channel).add(message), BuilderFactory.LONG);
  }

  public final CommandObject<Long> spublish(byte[] channel, byte[] message) {
    return new CommandObject<>(commandArguments(Command.SPUBLISH).key(channel).add(message), BuilderFactory.LONG);
  }
  // Miscellaneous commands

  // RediSearch commands
  public final CommandObject<Long> hsetObject(String key, String field, Object value) {
    return new CommandObject<>(commandArguments(Command.HSET).key(key).add(field).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<Long> hsetObject(String key, Map<String, Object> hash) {
    return new CommandObject<>(addFlatMapArgs(commandArguments(Command.HSET).key(key), hash), BuilderFactory.LONG);
  }

  private boolean isRoundRobinSearchCommand() {
    if (broadcastAndRoundRobinConfig == null) {
      return true;
    } else if (broadcastAndRoundRobinConfig.getRediSearchModeInCluster() == JedisBroadcastAndRoundRobinConfig.RediSearchMode.LIGHT) {
      return false;
    }
    return true;
  }

  private CommandArguments checkAndRoundRobinSearchCommand(SearchCommand sc, String idx) {
    CommandArguments ca = commandArguments(sc);
    if (isRoundRobinSearchCommand()) {
      ca.add(idx);
    } else {
      ca.key(idx);
    }
    return ca;
  }

  private CommandArguments checkAndRoundRobinSearchCommand(SearchCommand sc, String idx1, String idx2) {
    CommandArguments ca = commandArguments(sc);
    if (isRoundRobinSearchCommand()) {
      ca.add(idx1).add(idx2);
    } else {
      ca.key(idx1).key(idx2);
    }
    return ca;
  }

  private CommandArguments checkAndRoundRobinSearchCommand(CommandArguments commandArguments, byte[] indexName) {
    return isRoundRobinSearchCommand() ? commandArguments.add(indexName) : commandArguments.key(indexName);
  }

  private <T> CommandObject<T> directSearchCommand(CommandObject<T> object, String indexName) {
    object.getArguments().processKey(indexName);
    return object;
  }

  public final CommandObject<String> ftCreate(String indexName, IndexOptions indexOptions, Schema schema) {
    CommandArguments args = checkAndRoundRobinSearchCommand(SearchCommand.CREATE, indexName)
        .addParams(indexOptions).add(SearchKeyword.SCHEMA);
    schema.fields.forEach(field -> args.addParams(field));
    return new CommandObject<>(args, BuilderFactory.STRING);
  }

  public final CommandObject<String> ftCreate(String indexName, FTCreateParams createParams,
      Iterable<SchemaField> schemaFields) {
    CommandArguments args = checkAndRoundRobinSearchCommand(SearchCommand.CREATE, indexName)
        .addParams(createParams).add(SearchKeyword.SCHEMA);
    schemaFields.forEach(field -> args.addParams(field));
    return new CommandObject<>(args, BuilderFactory.STRING);
  }

  public final CommandObject<String> ftAlter(String indexName, Schema schema) {
    CommandArguments args = checkAndRoundRobinSearchCommand(SearchCommand.ALTER, indexName)
        .add(SearchKeyword.SCHEMA).add(SearchKeyword.ADD);
    schema.fields.forEach(field -> args.addParams(field));
    return new CommandObject<>(args, BuilderFactory.STRING);
  }

  public final CommandObject<String> ftAlter(String indexName, Iterable<SchemaField> schemaFields) {
    CommandArguments args = checkAndRoundRobinSearchCommand(SearchCommand.ALTER, indexName)
        .add(SearchKeyword.SCHEMA).add(SearchKeyword.ADD);
    schemaFields.forEach(field -> args.addParams(field));
    return new CommandObject<>(args, BuilderFactory.STRING);
  }

  public final CommandObject<String> ftAliasAdd(String aliasName, String indexName) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.ALIASADD, aliasName, indexName), BuilderFactory.STRING);
  }

  public final CommandObject<String> ftAliasUpdate(String aliasName, String indexName) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.ALIASUPDATE, aliasName, indexName), BuilderFactory.STRING);
  }

   public final CommandObject<String> ftAliasDel(String aliasName) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.ALIASDEL, aliasName), BuilderFactory.STRING);
  }

  public final CommandObject<String> ftDropIndex(String indexName) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.DROPINDEX, indexName), BuilderFactory.STRING);
  }

  public final CommandObject<String> ftDropIndexDD(String indexName) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.DROPINDEX, indexName).add(SearchKeyword.DD),
        BuilderFactory.STRING);
  }

  public final CommandObject<SearchResult> ftSearch(String indexName, String query) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.SEARCH, indexName).add(query),
        getSearchResultBuilder(() -> new SearchResultBuilder(true, false, true)));
  }

  public final CommandObject<SearchResult> ftSearch(String indexName, String query, FTSearchParams params) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.SEARCH, indexName)
        .add(query).addParams(params.dialectOptional(searchDialect.get())),
        getSearchResultBuilder(() -> new SearchResultBuilder(!params.getNoContent(), params.getWithScores(), true)));
  }

  public final CommandObject<SearchResult> ftSearch(String indexName, Query query) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.SEARCH, indexName)
        .addParams(query.dialectOptional(searchDialect.get())), getSearchResultBuilder(() -> 
        new SearchResultBuilder(!query.getNoContent(), query.getWithScores(), true)));
  }

  @Deprecated
  public final CommandObject<SearchResult> ftSearch(byte[] indexName, Query query) {
    if (protocol == RedisProtocol.RESP3) {
      throw new UnsupportedOperationException("binary ft.search is not implemented with resp3.");
    }
    return new CommandObject<>(checkAndRoundRobinSearchCommand(commandArguments(SearchCommand.SEARCH), indexName)
        .addParams(query.dialectOptional(searchDialect.get())), getSearchResultBuilder(() -> 
        new SearchResultBuilder(!query.getNoContent(), query.getWithScores(), false)));
  }

  public final CommandObject<String> ftExplain(String indexName, Query query) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.EXPLAIN, indexName)
        .addParams(query.dialectOptional(searchDialect.get())), BuilderFactory.STRING);
  }

  public final CommandObject<List<String>> ftExplainCLI(String indexName, Query query) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.EXPLAINCLI, indexName)
        .addParams(query.dialectOptional(searchDialect.get())), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<AggregationResult> ftAggregate(String indexName, AggregationBuilder aggr) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.AGGREGATE, indexName)
        .addParams(aggr.dialectOptional(searchDialect.get())), !aggr.isWithCursor() ? AggregationResult.SEARCH_AGGREGATION_RESULT
        : AggregationResult.SEARCH_AGGREGATION_RESULT_WITH_CURSOR);
  }

  public final CommandObject<AggregationResult> ftCursorRead(String indexName, long cursorId, int count) {
    return new CommandObject<>(commandArguments(SearchCommand.CURSOR).add(SearchKeyword.READ)
        .key(indexName).add(cursorId).add(SearchKeyword.COUNT).add(count),
        AggregationResult.SEARCH_AGGREGATION_RESULT_WITH_CURSOR);
  }

  public final CommandObject<String> ftCursorDel(String indexName, long cursorId) {
    return new CommandObject<>(commandArguments(SearchCommand.CURSOR).add(SearchKeyword.DEL)
        .key(indexName).add(cursorId), BuilderFactory.STRING);
  }

  public final CommandObject<Map.Entry<AggregationResult, Map<String, Object>>> ftProfileAggregate(
      String indexName, FTProfileParams profileParams, AggregationBuilder aggr) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.PROFILE, indexName)
        .add(SearchKeyword.AGGREGATE).addParams(profileParams).add(SearchKeyword.QUERY)
        .addParams(aggr.dialectOptional(searchDialect.get())), new SearchProfileResponseBuilder<>(
        !aggr.isWithCursor() ? AggregationResult.SEARCH_AGGREGATION_RESULT
        : AggregationResult.SEARCH_AGGREGATION_RESULT_WITH_CURSOR));
  }

  public final CommandObject<Map.Entry<SearchResult, Map<String, Object>>> ftProfileSearch(
      String indexName, FTProfileParams profileParams, Query query) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.PROFILE, indexName)
        .add(SearchKeyword.SEARCH).addParams(profileParams).add(SearchKeyword.QUERY)
        .addParams(query.dialectOptional(searchDialect.get())), new SearchProfileResponseBuilder<>(
            getSearchResultBuilder(() -> new SearchResultBuilder(!query.getNoContent(), query.getWithScores(), true))));
  }

  public final CommandObject<Map.Entry<SearchResult, Map<String, Object>>> ftProfileSearch(
      String indexName, FTProfileParams profileParams, String query, FTSearchParams searchParams) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.PROFILE, indexName)
        .add(SearchKeyword.SEARCH).addParams(profileParams).add(SearchKeyword.QUERY).add(query)
        .addParams(searchParams.dialectOptional(searchDialect.get())), new SearchProfileResponseBuilder<>(
            getSearchResultBuilder(() -> new SearchResultBuilder(!searchParams.getNoContent(), searchParams.getWithScores(), true))));
  }

  private Builder<SearchResult> getSearchResultBuilder(Supplier<Builder<SearchResult>> resp2) {
    if (protocol == RedisProtocol.RESP3) return SearchResult.SEARCH_RESULT_BUILDER;
    return resp2.get();
  }

  public final CommandObject<String> ftSynUpdate(String indexName, String synonymGroupId, String... terms) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.SYNUPDATE, indexName)
        .add(synonymGroupId).addObjects((Object[]) terms), BuilderFactory.STRING);
  }

  public final CommandObject<Map<String, List<String>>> ftSynDump(String indexName) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.SYNDUMP, indexName),
        SearchBuilderFactory.SEARCH_SYNONYM_GROUPS);
  }

  public final CommandObject<Long> ftDictAdd(String dictionary, String... terms) {
    return new CommandObject<>(commandArguments(SearchCommand.DICTADD).add(dictionary).addObjects((Object[]) terms),
        BuilderFactory.LONG);
  }

  public final CommandObject<Long> ftDictDel(String dictionary, String... terms) {
    return new CommandObject<>(commandArguments(SearchCommand.DICTDEL).add(dictionary).addObjects((Object[]) terms),
        BuilderFactory.LONG);
  }

  public final CommandObject<Set<String>> ftDictDump(String dictionary) {
    return new CommandObject<>(commandArguments(SearchCommand.DICTDUMP).add(dictionary), BuilderFactory.STRING_SET);
  }

  public final CommandObject<Long> ftDictAddBySampleKey(String indexName, String dictionary, String... terms) {
    return directSearchCommand(ftDictAdd(dictionary, terms), indexName);
  }

  public final CommandObject<Long> ftDictDelBySampleKey(String indexName, String dictionary, String... terms) {
    return directSearchCommand(ftDictDel(dictionary, terms), indexName);
  }

  public final CommandObject<Set<String>> ftDictDumpBySampleKey(String indexName, String dictionary) {
    return directSearchCommand(ftDictDump(dictionary), indexName);
  }

  public final CommandObject<Map<String, Map<String, Double>>> ftSpellCheck(String index, String query) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.SPELLCHECK, index).add(query),
        SearchBuilderFactory.SEARCH_SPELLCHECK_RESPONSE);
  }

  public final CommandObject<Map<String, Map<String, Double>>> ftSpellCheck(String index, String query,
      FTSpellCheckParams spellCheckParams) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.SPELLCHECK, index).add(query)
        .addParams(spellCheckParams.dialectOptional(searchDialect.get())), SearchBuilderFactory.SEARCH_SPELLCHECK_RESPONSE);
  }

  public final CommandObject<Map<String, Object>> ftInfo(String indexName) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.INFO, indexName),
        protocol == RedisProtocol.RESP3 ? BuilderFactory.AGGRESSIVE_ENCODED_OBJECT_MAP : BuilderFactory.ENCODED_OBJECT_MAP);
  }

  public final CommandObject<Set<String>> ftTagVals(String indexName, String fieldName) {
    return new CommandObject<>(checkAndRoundRobinSearchCommand(SearchCommand.TAGVALS, indexName)
        .add(fieldName), BuilderFactory.STRING_SET);
  }

  public final CommandObject<Map<String, Object>> ftConfigGet(String option) {
    return new CommandObject<>(commandArguments(SearchCommand.CONFIG).add(SearchKeyword.GET).add(option),
        protocol == RedisProtocol.RESP3 ? BuilderFactory.AGGRESSIVE_ENCODED_OBJECT_MAP : BuilderFactory.ENCODED_OBJECT_MAP_FROM_PAIRS);
  }

  public final CommandObject<Map<String, Object>> ftConfigGet(String indexName, String option) {
    return directSearchCommand(ftConfigGet(option), indexName);
  }

  public final CommandObject<String> ftConfigSet(String option, String value) {
    return new CommandObject<>(commandArguments(SearchCommand.CONFIG).add(SearchKeyword.SET).add(option).add(value), BuilderFactory.STRING);
  }

  public final CommandObject<String> ftConfigSet(String indexName, String option, String value) {
    return directSearchCommand(ftConfigSet(option, value), indexName);
  }

  public final CommandObject<Long> ftSugAdd(String key, String string, double score) {
    return new CommandObject<>(commandArguments(SearchCommand.SUGADD).key(key).add(string).add(score), BuilderFactory.LONG);
  }

  public final CommandObject<Long> ftSugAddIncr(String key, String string, double score) {
    return new CommandObject<>(commandArguments(SearchCommand.SUGADD).key(key).add(string).add(score).add(SearchKeyword.INCR), BuilderFactory.LONG);
  }

  public final CommandObject<List<String>> ftSugGet(String key, String prefix) {
    return new CommandObject<>(commandArguments(SearchCommand.SUGGET).key(key).add(prefix), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> ftSugGet(String key, String prefix, boolean fuzzy, int max) {
    CommandArguments args = commandArguments(SearchCommand.SUGGET).key(key).add(prefix);
    if (fuzzy) args.add(SearchKeyword.FUZZY);
    args.add(SearchKeyword.MAX).add(max);
    return new CommandObject<>(args, BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<Tuple>> ftSugGetWithScores(String key, String prefix) {
    return new CommandObject<>(commandArguments(SearchCommand.SUGGET).key(key).add(prefix)
        .add(SearchKeyword.WITHSCORES), BuilderFactory.TUPLE_LIST);
  }

  public final CommandObject<List<Tuple>> ftSugGetWithScores(String key, String prefix, boolean fuzzy, int max) {
    CommandArguments args = commandArguments(SearchCommand.SUGGET).key(key).add(prefix);
    if (fuzzy) args.add(SearchKeyword.FUZZY);
    args.add(SearchKeyword.MAX).add(max);
    args.add(SearchKeyword.WITHSCORES);
    return new CommandObject<>(args, BuilderFactory.TUPLE_LIST);
  }

  public final CommandObject<Boolean> ftSugDel(String key, String string) {
    return new CommandObject<>(commandArguments(SearchCommand.SUGDEL).key(key).add(string), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Long> ftSugLen(String key) {
    return new CommandObject<>(commandArguments(SearchCommand.SUGLEN).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Set<String>> ftList() {
    return new CommandObject<>(commandArguments(SearchCommand._LIST), BuilderFactory.STRING_SET);
  }
  // RediSearch commands

  // RedisJSON commands
  public final CommandObject<String> jsonSet(String key, Path2 path, Object object) {
    return new CommandObject<>(commandArguments(JsonCommand.SET).key(key).add(path).add(object), BuilderFactory.STRING);
  }

  public final CommandObject<String> jsonSetWithEscape(String key, Path2 path, Object object) {
    return new CommandObject<>(commandArguments(JsonCommand.SET).key(key).add(path).add(
        getJsonObjectMapper().toJson(object)), BuilderFactory.STRING);
  }

  @Deprecated
  public final CommandObject<String> jsonSet(String key, Path path, Object pojo) {
    return new CommandObject<>(commandArguments(JsonCommand.SET).key(key).add(path).add(
        getJsonObjectMapper().toJson(pojo)), BuilderFactory.STRING);
  }

  @Deprecated
  public final CommandObject<String> jsonSetWithPlainString(String key, Path path, String string) {
    return new CommandObject<>(commandArguments(JsonCommand.SET).key(key).add(path).add(string), BuilderFactory.STRING);
  }

  public final CommandObject<String> jsonSet(String key, Path2 path, Object object, JsonSetParams params) {
    return new CommandObject<>(commandArguments(JsonCommand.SET).key(key).add(path).add(object).addParams(params), BuilderFactory.STRING);
  }

  public final CommandObject<String> jsonSetWithEscape(String key, Path2 path, Object object, JsonSetParams params) {
    return new CommandObject<>(commandArguments(JsonCommand.SET).key(key).add(path).add(
        getJsonObjectMapper().toJson(object)).addParams(params), BuilderFactory.STRING);
  }

  @Deprecated
  public final CommandObject<String> jsonSet(String key, Path path, Object pojo, JsonSetParams params) {
    return new CommandObject<>(commandArguments(JsonCommand.SET).key(key).add(path).add(
        getJsonObjectMapper().toJson(pojo)).addParams(params), BuilderFactory.STRING);
  }

  public final CommandObject<String> jsonMerge(String key, Path2 path, Object object) {
    return new CommandObject<>(commandArguments(JsonCommand.MERGE).key(key).add(path).add(object), BuilderFactory.STRING);
  }

  @Deprecated
  public final CommandObject<String> jsonMerge(String key, Path path, Object pojo) {
    return new CommandObject<>(commandArguments(JsonCommand.MERGE).key(key).add(path).add(
        getJsonObjectMapper().toJson(pojo)), BuilderFactory.STRING);
  }

  public final CommandObject<Object> jsonGet(String key) {
    return new CommandObject<>(commandArguments(JsonCommand.GET).key(key),
        protocol != RedisProtocol.RESP3 ? JSON_GENERIC_OBJECT : JsonBuilderFactory.JSON_OBJECT);
  }

  @Deprecated
  public final <T> CommandObject<T> jsonGet(String key, Class<T> clazz) {
    return new CommandObject<>(commandArguments(JsonCommand.GET).key(key), new JsonObjectBuilder<>(clazz));
  }

  public final CommandObject<Object> jsonGet(String key, Path2... paths) {
    return new CommandObject<>(commandArguments(JsonCommand.GET).key(key).addObjects((Object[]) paths), JsonBuilderFactory.JSON_OBJECT);
  }

  @Deprecated
  public final CommandObject<Object> jsonGet(String key, Path... paths) {
    return new CommandObject<>(commandArguments(JsonCommand.GET).key(key).addObjects((Object[]) paths), JSON_GENERIC_OBJECT);
  }

  @Deprecated
  public final CommandObject<String> jsonGetAsPlainString(String key, Path path) {
    return new CommandObject<>(commandArguments(JsonCommand.GET).key(key).add(path), BuilderFactory.STRING);
  }

  @Deprecated
  public final <T> CommandObject<T> jsonGet(String key, Class<T> clazz, Path... paths) {
    return new CommandObject<>(commandArguments(JsonCommand.GET).key(key).addObjects((Object[]) paths), new JsonObjectBuilder<>(clazz));
  }

  public final CommandObject<List<JSONArray>> jsonMGet(Path2 path, String... keys) {
    return new CommandObject<>(commandArguments(JsonCommand.MGET).keys((Object[]) keys).add(path), JsonBuilderFactory.JSON_ARRAY_LIST);
  }

  @Deprecated
  public final <T> CommandObject<List<T>> jsonMGet(Path path, Class<T> clazz, String... keys) {
    return new CommandObject<>(commandArguments(JsonCommand.MGET).keys((Object[]) keys).add(path), new JsonObjectListBuilder<>(clazz));
  }

  public final CommandObject<Long> jsonDel(String key) {
    return new CommandObject<>(commandArguments(JsonCommand.DEL).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> jsonDel(String key, Path2 path) {
    return new CommandObject<>(commandArguments(JsonCommand.DEL).key(key).add(path), BuilderFactory.LONG);
  }

  @Deprecated
  public final CommandObject<Long> jsonDel(String key, Path path) {
    return new CommandObject<>(commandArguments(JsonCommand.DEL).key(key).add(path), BuilderFactory.LONG);
  }

  public final CommandObject<Long> jsonClear(String key) {
    return new CommandObject<>(commandArguments(JsonCommand.CLEAR).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Long> jsonClear(String key, Path2 path) {
    return new CommandObject<>(commandArguments(JsonCommand.CLEAR).key(key).add(path), BuilderFactory.LONG);
  }

  @Deprecated
  public final CommandObject<Long> jsonClear(String key, Path path) {
    return new CommandObject<>(commandArguments(JsonCommand.CLEAR).key(key).add(path), BuilderFactory.LONG);
  }

  public final CommandObject<List<Boolean>> jsonToggle(String key, Path2 path) {
    return new CommandObject<>(commandArguments(JsonCommand.TOGGLE).key(key).add(path), BuilderFactory.BOOLEAN_LIST);
  }

  @Deprecated
  public final CommandObject<String> jsonToggle(String key, Path path) {
    return new CommandObject<>(commandArguments(JsonCommand.TOGGLE).key(key).add(path), BuilderFactory.STRING);
  }

  @Deprecated
  public final CommandObject<Class<?>> jsonType(String key) {
    return new CommandObject<>(commandArguments(JsonCommand.TYPE).key(key), JsonBuilderFactory.JSON_TYPE);
  }

  public final CommandObject<List<Class<?>>> jsonType(String key, Path2 path) {
    return new CommandObject<>(commandArguments(JsonCommand.TYPE).key(key).add(path),
        protocol != RedisProtocol.RESP3 ? JsonBuilderFactory.JSON_TYPE_LIST : JsonBuilderFactory.JSON_TYPE_RESPONSE_RESP3_COMPATIBLE);
  }

  @Deprecated
  public final CommandObject<Class<?>> jsonType(String key, Path path) {
    return new CommandObject<>(commandArguments(JsonCommand.TYPE).key(key).add(path), JsonBuilderFactory.JSON_TYPE);
  }

  @Deprecated
  public final CommandObject<Long> jsonStrAppend(String key, Object string) {
    return new CommandObject<>(commandArguments(JsonCommand.STRAPPEND).key(key).add(
        getJsonObjectMapper().toJson(string)), BuilderFactory.LONG);
  }

  public final CommandObject<List<Long>> jsonStrAppend(String key, Path2 path, Object string) {
    return new CommandObject<>(commandArguments(JsonCommand.STRAPPEND).key(key).add(path).add(
        getJsonObjectMapper().toJson(string)), BuilderFactory.LONG_LIST);
  }

  @Deprecated
  public final CommandObject<Long> jsonStrAppend(String key, Path path, Object string) {
    return new CommandObject<>(commandArguments(JsonCommand.STRAPPEND).key(key).add(path).add(
        getJsonObjectMapper().toJson(string)), BuilderFactory.LONG);
  }

  @Deprecated
  public final CommandObject<Long> jsonStrLen(String key) {
    return new CommandObject<>(commandArguments(JsonCommand.STRLEN).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<List<Long>> jsonStrLen(String key, Path2 path) {
    return new CommandObject<>(commandArguments(JsonCommand.STRLEN).key(key).add(path), BuilderFactory.LONG_LIST);
  }

  @Deprecated
  public final CommandObject<Long> jsonStrLen(String key, Path path) {
    return new CommandObject<>(commandArguments(JsonCommand.STRLEN).key(key).add(path), BuilderFactory.LONG);
  }

  public final CommandObject<Object> jsonNumIncrBy(String key, Path2 path, double value) {
    return new CommandObject<>(commandArguments(JsonCommand.NUMINCRBY).key(key).add(path).add(value),
        JsonBuilderFactory.JSON_ARRAY_OR_DOUBLE_LIST);
  }

  @Deprecated
  public final CommandObject<Double> jsonNumIncrBy(String key, Path path, double value) {
    return new CommandObject<>(commandArguments(JsonCommand.NUMINCRBY).key(key).add(path).add(value), BuilderFactory.DOUBLE);
  }

  @Deprecated
  public final CommandObject<Long> jsonArrAppend(String key, String path, JSONObject... objects) {
    CommandArguments args = commandArguments(JsonCommand.ARRAPPEND).key(key).add(path);
    for (Object object : objects) {
      args.add(object);
    }
    return new CommandObject<>(args, BuilderFactory.LONG);
  }

  public final CommandObject<List<Long>> jsonArrAppend(String key, Path2 path, Object... objects) {
    CommandArguments args = commandArguments(JsonCommand.ARRAPPEND).key(key).add(path).addObjects(objects);
    return new CommandObject<>(args, BuilderFactory.LONG_LIST);
  }

  public final CommandObject<List<Long>> jsonArrAppendWithEscape(String key, Path2 path, Object... objects) {
    CommandArguments args = commandArguments(JsonCommand.ARRAPPEND).key(key).add(path);
    for (Object object : objects) {
      args.add(getJsonObjectMapper().toJson(object));
    }
    return new CommandObject<>(args, BuilderFactory.LONG_LIST);
  }

  @Deprecated
  public final CommandObject<Long> jsonArrAppend(String key, Path path, Object... pojos) {
    CommandArguments args = commandArguments(JsonCommand.ARRAPPEND).key(key).add(path);
    for (Object pojo : pojos) {
      args.add(getJsonObjectMapper().toJson(pojo));
    }
    return new CommandObject<>(args, BuilderFactory.LONG);
  }

  public final CommandObject<List<Long>> jsonArrIndex(String key, Path2 path, Object scalar) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRINDEX).key(key).add(path).add(scalar), BuilderFactory.LONG_LIST);
  }

  public final CommandObject<List<Long>> jsonArrIndexWithEscape(String key, Path2 path, Object scalar) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRINDEX).key(key).add(path).add(
        getJsonObjectMapper().toJson(scalar)), BuilderFactory.LONG_LIST);
  }

  @Deprecated
  public final CommandObject<Long> jsonArrIndex(String key, Path path, Object scalar) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRINDEX).key(key).add(path).add(
        getJsonObjectMapper().toJson(scalar)), BuilderFactory.LONG);
  }

  public final CommandObject<List<Long>> jsonArrInsert(String key, Path2 path, int index, Object... objects) {
    CommandArguments args = commandArguments(JsonCommand.ARRINSERT).key(key).add(path).add(index).addObjects(objects);
    return new CommandObject<>(args, BuilderFactory.LONG_LIST);
  }

  public final CommandObject<List<Long>> jsonArrInsertWithEscape(String key, Path2 path, int index, Object... objects) {
    CommandArguments args = commandArguments(JsonCommand.ARRINSERT).key(key).add(path).add(index);
    for (Object object : objects) {
      args.add(getJsonObjectMapper().toJson(object));
    }
    return new CommandObject<>(args, BuilderFactory.LONG_LIST);
  }

  @Deprecated
  public final CommandObject<Long> jsonArrInsert(String key, Path path, int index, Object... pojos) {
    CommandArguments args = commandArguments(JsonCommand.ARRINSERT).key(key).add(path).add(index);
    for (Object pojo : pojos) {
      args.add(getJsonObjectMapper().toJson(pojo));
    }
    return new CommandObject<>(args, BuilderFactory.LONG);
  }

  @Deprecated
  public final CommandObject<Object> jsonArrPop(String key) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRPOP).key(key), new JsonObjectBuilder<>(Object.class));
  }

  @Deprecated
  public final <T> CommandObject<T> jsonArrPop(String key, Class<T> clazz) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRPOP).key(key), new JsonObjectBuilder<>(clazz));
  }

  public final CommandObject<List<Object>> jsonArrPop(String key, Path2 path) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRPOP).key(key).add(path), new JsonObjectListBuilder<>(Object.class));
  }

  @Deprecated
  public final CommandObject<Object> jsonArrPop(String key, Path path) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRPOP).key(key).add(path), new JsonObjectBuilder<>(Object.class));
  }

  @Deprecated
  public final <T> CommandObject<T> jsonArrPop(String key, Class<T> clazz, Path path) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRPOP).key(key).add(path), new JsonObjectBuilder<>(clazz));
  }

  public final CommandObject<List<Object>> jsonArrPop(String key, Path2 path, int index) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRPOP).key(key).add(path).add(index), new JsonObjectListBuilder<>(Object.class));
  }

  @Deprecated
  public final CommandObject<Object> jsonArrPop(String key, Path path, int index) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRPOP).key(key).add(path).add(index), new JsonObjectBuilder<>(Object.class));
  }

  @Deprecated
  public final <T> CommandObject<T> jsonArrPop(String key, Class<T> clazz, Path path, int index) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRPOP).key(key).add(path).add(index), new JsonObjectBuilder<>(clazz));
  }

  @Deprecated
  public final CommandObject<Long> jsonArrLen(String key) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRLEN).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<List<Long>> jsonArrLen(String key, Path2 path) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRLEN).key(key).add(path), BuilderFactory.LONG_LIST);
  }

  @Deprecated
  public final CommandObject<Long> jsonArrLen(String key, Path path) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRLEN).key(key).add(path), BuilderFactory.LONG);
  }

  public final CommandObject<List<Long>> jsonArrTrim(String key, Path2 path, int start, int stop) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRTRIM).key(key).add(path).add(start).add(stop), BuilderFactory.LONG_LIST);
  }

  @Deprecated
  public final CommandObject<Long> jsonArrTrim(String key, Path path, int start, int stop) {
    return new CommandObject<>(commandArguments(JsonCommand.ARRTRIM).key(key).add(path).add(start).add(stop), BuilderFactory.LONG);
  }

  @Deprecated
  public final CommandObject<Long> jsonObjLen(String key) {
    return new CommandObject<>(commandArguments(JsonCommand.OBJLEN).key(key), BuilderFactory.LONG);
  }

  @Deprecated
  public final CommandObject<Long> jsonObjLen(String key, Path path) {
    return new CommandObject<>(commandArguments(JsonCommand.OBJLEN).key(key).add(path), BuilderFactory.LONG);
  }

  public final CommandObject<List<Long>> jsonObjLen(String key, Path2 path) {
    return new CommandObject<>(commandArguments(JsonCommand.OBJLEN).key(key).add(path), BuilderFactory.LONG_LIST);
  }

  @Deprecated
  public final CommandObject<List<String>> jsonObjKeys(String key) {
    return new CommandObject<>(commandArguments(JsonCommand.OBJKEYS).key(key), BuilderFactory.STRING_LIST);
  }

  @Deprecated
  public final CommandObject<List<String>> jsonObjKeys(String key, Path path) {
    return new CommandObject<>(commandArguments(JsonCommand.OBJKEYS).key(key).add(path), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<List<String>>> jsonObjKeys(String key, Path2 path) {
    return new CommandObject<>(commandArguments(JsonCommand.OBJKEYS).key(key).add(path), BuilderFactory.STRING_LIST_LIST);
  }

  @Deprecated
  public final CommandObject<Long> jsonDebugMemory(String key) {
    return new CommandObject<>(commandArguments(JsonCommand.DEBUG).add("MEMORY").key(key), BuilderFactory.LONG);
  }

  @Deprecated
  public final CommandObject<Long> jsonDebugMemory(String key, Path path) {
    return new CommandObject<>(commandArguments(JsonCommand.DEBUG).add("MEMORY").key(key).add(path), BuilderFactory.LONG);
  }

  public final CommandObject<List<Long>> jsonDebugMemory(String key, Path2 path) {
    return new CommandObject<>(commandArguments(JsonCommand.DEBUG).add("MEMORY").key(key).add(path), BuilderFactory.LONG_LIST);
  }
  // RedisJSON commands

  // RedisTimeSeries commands
  public final CommandObject<String> tsCreate(String key) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.CREATE).key(key), BuilderFactory.STRING);
  }

  public final CommandObject<String> tsCreate(String key, TSCreateParams createParams) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.CREATE).key(key).addParams(createParams), BuilderFactory.STRING);
  }

  public final CommandObject<Long> tsDel(String key, long fromTimestamp, long toTimestamp) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.DEL).key(key)
        .add(fromTimestamp).add(toTimestamp), BuilderFactory.LONG);
  }

  public final CommandObject<String> tsAlter(String key, TSAlterParams alterParams) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.ALTER).key(key).addParams(alterParams), BuilderFactory.STRING);
  }

  public final CommandObject<Long> tsAdd(String key, double value) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.ADD).key(key).add(Protocol.BYTES_ASTERISK).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<Long> tsAdd(String key, long timestamp, double value) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.ADD).key(key).add(timestamp).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<Long> tsAdd(String key, long timestamp, double value, TSCreateParams createParams) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.ADD).key(key)
        .add(timestamp).add(value).addParams(createParams), BuilderFactory.LONG);
  }

  public final CommandObject<List<Long>> tsMAdd(Map.Entry<String, TSElement>... entries) {
    CommandArguments args = commandArguments(TimeSeriesCommand.MADD);
    for (Map.Entry<String, TSElement> entry : entries) {
      args.key(entry.getKey()).add(entry.getValue().getTimestamp()).add(entry.getValue().getValue());
    }
    return new CommandObject<>(args, BuilderFactory.LONG_LIST);
  }

  public final CommandObject<Long> tsIncrBy(String key, double value) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.INCRBY).key(key).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<Long> tsIncrBy(String key, double value, long timestamp) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.INCRBY).key(key).add(value)
        .add(TimeSeriesKeyword.TIMESTAMP).add(timestamp), BuilderFactory.LONG);
  }

  public final CommandObject<Long> tsDecrBy(String key, double value) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.DECRBY).key(key).add(value), BuilderFactory.LONG);
  }

  public final CommandObject<Long> tsDecrBy(String key, double value, long timestamp) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.DECRBY).key(key).add(value)
        .add(TimeSeriesKeyword.TIMESTAMP).add(timestamp), BuilderFactory.LONG);
  }

  public final CommandObject<List<TSElement>> tsRange(String key, long fromTimestamp, long toTimestamp) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.RANGE).key(key)
        .add(fromTimestamp).add(toTimestamp), TimeSeriesBuilderFactory.TIMESERIES_ELEMENT_LIST);
  }

  public final CommandObject<List<TSElement>> tsRange(String key, TSRangeParams rangeParams) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.RANGE).key(key)
        .addParams(rangeParams), TimeSeriesBuilderFactory.TIMESERIES_ELEMENT_LIST);
  }

  public final CommandObject<List<TSElement>> tsRevRange(String key, long fromTimestamp, long toTimestamp) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.REVRANGE).key(key)
        .add(fromTimestamp).add(toTimestamp), TimeSeriesBuilderFactory.TIMESERIES_ELEMENT_LIST);
  }

  public final CommandObject<List<TSElement>> tsRevRange(String key, TSRangeParams rangeParams) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.REVRANGE).key(key)
        .addParams(rangeParams), TimeSeriesBuilderFactory.TIMESERIES_ELEMENT_LIST);
  }

  public final CommandObject<Map<String, TSMRangeElements>> tsMRange(long fromTimestamp, long toTimestamp, String... filters) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.MRANGE).add(fromTimestamp)
        .add(toTimestamp).add(TimeSeriesKeyword.FILTER).addObjects((Object[]) filters),
        getTimeseriesMultiRangeResponseBuilder());
  }

  public final CommandObject<Map<String, TSMRangeElements>> tsMRange(TSMRangeParams multiRangeParams) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.MRANGE)
        .addParams(multiRangeParams), getTimeseriesMultiRangeResponseBuilder());
  }

  public final CommandObject<Map<String, TSMRangeElements>> tsMRevRange(long fromTimestamp, long toTimestamp, String... filters) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.MREVRANGE).add(fromTimestamp)
        .add(toTimestamp).add(TimeSeriesKeyword.FILTER).addObjects((Object[]) filters),
        getTimeseriesMultiRangeResponseBuilder());
  }

  public final CommandObject<Map<String, TSMRangeElements>> tsMRevRange(TSMRangeParams multiRangeParams) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.MREVRANGE).addParams(multiRangeParams),
        getTimeseriesMultiRangeResponseBuilder());
  }

  public final CommandObject<TSElement> tsGet(String key) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.GET).key(key), TimeSeriesBuilderFactory.TIMESERIES_ELEMENT);
  }

  public final CommandObject<TSElement> tsGet(String key, TSGetParams getParams) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.GET).key(key).addParams(getParams), TimeSeriesBuilderFactory.TIMESERIES_ELEMENT);
  }

  public final CommandObject<Map<String, TSMGetElement>> tsMGet(TSMGetParams multiGetParams, String... filters) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.MGET).addParams(multiGetParams)
        .add(TimeSeriesKeyword.FILTER).addObjects((Object[]) filters),
        protocol == RedisProtocol.RESP3 ? TimeSeriesBuilderFactory.TIMESERIES_MGET_RESPONSE_RESP3
            : TimeSeriesBuilderFactory.TIMESERIES_MGET_RESPONSE);
  }

  public final CommandObject<String> tsCreateRule(String sourceKey, String destKey, AggregationType aggregationType,
      long timeBucket) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.CREATERULE).key(sourceKey).key(destKey)
        .add(TimeSeriesKeyword.AGGREGATION).add(aggregationType).add(timeBucket), BuilderFactory.STRING);
  }

  public final CommandObject<String> tsCreateRule(String sourceKey, String destKey, AggregationType aggregationType,
      long bucketDuration, long alignTimestamp) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.CREATERULE).key(sourceKey).key(destKey)
        .add(TimeSeriesKeyword.AGGREGATION).add(aggregationType).add(bucketDuration).add(alignTimestamp), BuilderFactory.STRING);
  }

  public final CommandObject<String> tsDeleteRule(String sourceKey, String destKey) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.DELETERULE).key(sourceKey).key(destKey), BuilderFactory.STRING);
  }

  public final CommandObject<List<String>> tsQueryIndex(String... filters) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.QUERYINDEX)
        .addObjects((Object[]) filters), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<TSInfo> tsInfo(String key) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.INFO).key(key), getTimeseriesInfoBuilder());
  }

  public final CommandObject<TSInfo> tsInfoDebug(String key) {
    return new CommandObject<>(commandArguments(TimeSeriesCommand.INFO).key(key).add(TimeSeriesKeyword.DEBUG),
        getTimeseriesInfoBuilder());
  }

  private Builder<Map<String, TSMRangeElements>> getTimeseriesMultiRangeResponseBuilder() {
    return protocol == RedisProtocol.RESP3 ? TimeSeriesBuilderFactory.TIMESERIES_MRANGE_RESPONSE_RESP3
        : TimeSeriesBuilderFactory.TIMESERIES_MRANGE_RESPONSE;
  }

  private Builder<TSInfo> getTimeseriesInfoBuilder() {
    return protocol == RedisProtocol.RESP3 ? TSInfo.TIMESERIES_INFO_RESP3 : TSInfo.TIMESERIES_INFO;
  }
  // RedisTimeSeries commands

  // RedisBloom commands
  public final CommandObject<String> bfReserve(String key, double errorRate, long capacity) {
    return new CommandObject<>(commandArguments(BloomFilterCommand.RESERVE).key(key)
        .add(errorRate).add(capacity), BuilderFactory.STRING);
  }

  public final CommandObject<String> bfReserve(String key, double errorRate, long capacity, BFReserveParams reserveParams) {
    return new CommandObject<>(commandArguments(BloomFilterCommand.RESERVE).key(key)
        .add(errorRate).add(capacity).addParams(reserveParams), BuilderFactory.STRING);
  }

  public final CommandObject<Boolean> bfAdd(String key, String item) {
    return new CommandObject<>(commandArguments(BloomFilterCommand.ADD).key(key).add(item), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<List<Boolean>> bfMAdd(String key, String... items) {
    return new CommandObject<>(commandArguments(BloomFilterCommand.MADD).key(key).
        addObjects((Object[]) items), BuilderFactory.BOOLEAN_WITH_ERROR_LIST);
  }

  public final CommandObject<List<Boolean>> bfInsert(String key, String... items) {
    return new CommandObject<>(commandArguments(BloomFilterCommand.INSERT).key(key)
        .add(RedisBloomKeyword.ITEMS).addObjects((Object[]) items), BuilderFactory.BOOLEAN_WITH_ERROR_LIST);
  }

  public final CommandObject<List<Boolean>> bfInsert(String key, BFInsertParams insertParams, String... items) {
    return new CommandObject<>(commandArguments(BloomFilterCommand.INSERT).key(key).addParams(insertParams)
        .add(RedisBloomKeyword.ITEMS).addObjects((Object[]) items), BuilderFactory.BOOLEAN_WITH_ERROR_LIST);
  }

  public final CommandObject<Boolean> bfExists(String key, String item) {
    return new CommandObject<>(commandArguments(BloomFilterCommand.EXISTS).key(key).add(item), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<List<Boolean>> bfMExists(String key, String... items) {
    return new CommandObject<>(commandArguments(BloomFilterCommand.MEXISTS).key(key).
        addObjects((Object[]) items), BuilderFactory.BOOLEAN_LIST);
  }

  public final CommandObject<Map.Entry<Long, byte[]>> bfScanDump(String key, long iterator) {
    return new CommandObject<>(commandArguments(BloomFilterCommand.SCANDUMP).key(key).add(iterator), BLOOM_SCANDUMP_RESPONSE);
  }

  public final CommandObject<String> bfLoadChunk(String key, long iterator, byte[] data) {
    return new CommandObject<>(commandArguments(BloomFilterCommand.LOADCHUNK).key(key).add(iterator).add(data), BuilderFactory.STRING);
  }

  public final CommandObject<Long> bfCard(String key) {
    return new CommandObject<>(commandArguments(BloomFilterCommand.CARD).key(key), BuilderFactory.LONG);
  }

  public final CommandObject<Map<String, Object>> bfInfo(String key) {
    return new CommandObject<>(commandArguments(BloomFilterCommand.INFO).key(key), BuilderFactory.ENCODED_OBJECT_MAP);
  }

  public final CommandObject<String> cfReserve(String key, long capacity) {
    return new CommandObject<>(commandArguments(CuckooFilterCommand.RESERVE).key(key).add(capacity), BuilderFactory.STRING);
  }

  public final CommandObject<String> cfReserve(String key, long capacity, CFReserveParams reserveParams) {
    return new CommandObject<>(commandArguments(CuckooFilterCommand.RESERVE).key(key).add(capacity).addParams(reserveParams), BuilderFactory.STRING);
  }

  public final CommandObject<Boolean> cfAdd(String key, String item) {
    return new CommandObject<>(commandArguments(CuckooFilterCommand.ADD).key(key).add(item), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Boolean> cfAddNx(String key, String item) {
    return new CommandObject<>(commandArguments(CuckooFilterCommand.ADDNX).key(key).add(item), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<List<Boolean>> cfInsert(String key, String... items) {
    return new CommandObject<>(commandArguments(CuckooFilterCommand.INSERT).key(key)
        .add(RedisBloomKeyword.ITEMS).addObjects((Object[]) items), BuilderFactory.BOOLEAN_LIST);
  }

  public final CommandObject<List<Boolean>> cfInsert(String key, CFInsertParams insertParams, String... items) {
    return new CommandObject<>(commandArguments(CuckooFilterCommand.INSERT).key(key)
        .addParams(insertParams).add(RedisBloomKeyword.ITEMS).addObjects((Object[]) items), BuilderFactory.BOOLEAN_LIST);
  }

  public final CommandObject<List<Boolean>> cfInsertNx(String key, String... items) {
    return new CommandObject<>(commandArguments(CuckooFilterCommand.INSERTNX).key(key)
        .add(RedisBloomKeyword.ITEMS).addObjects((Object[]) items), BuilderFactory.BOOLEAN_LIST);
  }

  public final CommandObject<List<Boolean>> cfInsertNx(String key, CFInsertParams insertParams, String... items) {
    return new CommandObject<>(commandArguments(CuckooFilterCommand.INSERTNX).key(key)
        .addParams(insertParams).add(RedisBloomKeyword.ITEMS).addObjects((Object[]) items), BuilderFactory.BOOLEAN_LIST);
  }

  public final CommandObject<Boolean> cfExists(String key, String item) {
    return new CommandObject<>(commandArguments(CuckooFilterCommand.EXISTS).key(key).add(item), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<List<Boolean>> cfMExists(String key, String... items) {
    return new CommandObject<>(commandArguments(CuckooFilterCommand.MEXISTS).key(key)
        .addObjects((Object[]) items), BuilderFactory.BOOLEAN_LIST);
  }

  public final CommandObject<Boolean> cfDel(String key, String item) {
    return new CommandObject<>(commandArguments(CuckooFilterCommand.DEL).key(key).add(item), BuilderFactory.BOOLEAN);
  }

  public final CommandObject<Long> cfCount(String key, String item) {
    return new CommandObject<>(commandArguments(CuckooFilterCommand.COUNT).key(key).add(item), BuilderFactory.LONG);
  }

  public final CommandObject<Map.Entry<Long, byte[]>> cfScanDump(String key, long iterator) {
    return new CommandObject<>(commandArguments(CuckooFilterCommand.SCANDUMP).key(key).add(iterator), BLOOM_SCANDUMP_RESPONSE);
  }

  public final CommandObject<String> cfLoadChunk(String key, long iterator, byte[] data) {
    return new CommandObject<>(commandArguments(CuckooFilterCommand.LOADCHUNK).key(key).add(iterator).add(data), BuilderFactory.STRING);
  }

  public final CommandObject<Map<String, Object>> cfInfo(String key) {
    return new CommandObject<>(commandArguments(CuckooFilterCommand.INFO).key(key), BuilderFactory.ENCODED_OBJECT_MAP);
  }

  public final CommandObject<String> cmsInitByDim(String key, long width, long depth) {
    return new CommandObject<>(commandArguments(CountMinSketchCommand.INITBYDIM).key(key).add(width)
        .add(depth), BuilderFactory.STRING);
  }

  public final CommandObject<String> cmsInitByProb(String key, double error, double probability) {
    return new CommandObject<>(commandArguments(CountMinSketchCommand.INITBYPROB).key(key).add(error)
        .add(probability), BuilderFactory.STRING);
  }

  public final CommandObject<List<Long>> cmsIncrBy(String key, Map<String, Long> itemIncrements) {
    CommandArguments args = commandArguments(CountMinSketchCommand.INCRBY).key(key);
    itemIncrements.entrySet().forEach(entry -> args.add(entry.getKey()).add(entry.getValue()));
    return new CommandObject<>(args, BuilderFactory.LONG_LIST);
  }

  public final CommandObject<List<Long>> cmsQuery(String key, String... items) {
    return new CommandObject<>(commandArguments(CountMinSketchCommand.QUERY).key(key)
        .addObjects((Object[]) items), BuilderFactory.LONG_LIST);
  }

  public final CommandObject<String> cmsMerge(String destKey, String... keys) {
    return new CommandObject<>(commandArguments(CountMinSketchCommand.MERGE).key(destKey)
        .add(keys.length).keys((Object[]) keys), BuilderFactory.STRING);
  }

  public final CommandObject<String> cmsMerge(String destKey, Map<String, Long> keysAndWeights) {
    CommandArguments args = commandArguments(CountMinSketchCommand.MERGE).key(destKey);
    args.add(keysAndWeights.size());
    keysAndWeights.entrySet().forEach(entry -> args.key(entry.getKey()));
    args.add(RedisBloomKeyword.WEIGHTS);
    keysAndWeights.entrySet().forEach(entry -> args.add(entry.getValue()));
    return new CommandObject<>(args, BuilderFactory.STRING);
  }

  public final CommandObject<Map<String, Object>> cmsInfo(String key) {
    return new CommandObject<>(commandArguments(CountMinSketchCommand.INFO).key(key), BuilderFactory.ENCODED_OBJECT_MAP);
  }

  public final CommandObject<String> topkReserve(String key, long topk) {
    return new CommandObject<>(commandArguments(TopKCommand.RESERVE).key(key).add(topk), BuilderFactory.STRING);
  }

  public final CommandObject<String> topkReserve(String key, long topk, long width, long depth, double decay) {
    return new CommandObject<>(commandArguments(TopKCommand.RESERVE).key(key).add(topk)
        .add(width).add(depth).add(decay), BuilderFactory.STRING);
  }

  public final CommandObject<List<String>> topkAdd(String key, String... items) {
    return new CommandObject<>(commandArguments(TopKCommand.ADD).key(key).addObjects((Object[]) items), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<String>> topkIncrBy(String key, Map<String, Long> itemIncrements) {
    CommandArguments args = commandArguments(TopKCommand.INCRBY).key(key);
    itemIncrements.entrySet().forEach(entry -> args.add(entry.getKey()).add(entry.getValue()));
    return new CommandObject<>(args, BuilderFactory.STRING_LIST);
  }

  public final CommandObject<List<Boolean>> topkQuery(String key, String... items) {
    return new CommandObject<>(commandArguments(TopKCommand.QUERY).key(key).addObjects((Object[]) items), BuilderFactory.BOOLEAN_LIST);
  }

  public final CommandObject<List<String>> topkList(String key) {
    return new CommandObject<>(commandArguments(TopKCommand.LIST).key(key), BuilderFactory.STRING_LIST);
  }

  public final CommandObject<Map<String, Long>> topkListWithCount(String key) {
    return new CommandObject<>(commandArguments(TopKCommand.LIST).key(key)
        .add(RedisBloomKeyword.WITHCOUNT), BuilderFactory.STRING_LONG_MAP);
  }

  public final CommandObject<Map<String, Object>> topkInfo(String key) {
    return new CommandObject<>(commandArguments(TopKCommand.INFO).key(key), BuilderFactory.ENCODED_OBJECT_MAP);
  }

  public final CommandObject<String> tdigestCreate(String key) {
    return new CommandObject<>(commandArguments(TDigestCommand.CREATE).key(key), BuilderFactory.STRING);
  }

  public final CommandObject<String> tdigestCreate(String key, int compression) {
    return new CommandObject<>(commandArguments(TDigestCommand.CREATE).key(key).add(RedisBloomKeyword.COMPRESSION)
        .add(compression), BuilderFactory.STRING);
  }

  public final CommandObject<String> tdigestReset(String key) {
    return new CommandObject<>(commandArguments(TDigestCommand.RESET).key(key), BuilderFactory.STRING);
  }

  public final CommandObject<String> tdigestMerge(String destinationKey, String... sourceKeys) {
    return new CommandObject<>(commandArguments(TDigestCommand.MERGE).key(destinationKey)
        .add(sourceKeys.length).keys((Object[]) sourceKeys), BuilderFactory.STRING);
  }

  public final CommandObject<String> tdigestMerge(TDigestMergeParams mergeParams,
      String destinationKey, String... sourceKeys) {
    return new CommandObject<>(commandArguments(TDigestCommand.MERGE).key(destinationKey)
        .add(sourceKeys.length).keys((Object[]) sourceKeys).addParams(mergeParams), BuilderFactory.STRING);
  }

  public final CommandObject<Map<String, Object>> tdigestInfo(String key) {
    return new CommandObject<>(commandArguments(TDigestCommand.INFO).key(key), BuilderFactory.ENCODED_OBJECT_MAP);
  }

  public final CommandObject<String> tdigestAdd(String key, double... values) {
    return new CommandObject<>(addFlatArgs(commandArguments(TDigestCommand.ADD).key(key), values),
        BuilderFactory.STRING);
  }

  public final CommandObject<List<Double>> tdigestCDF(String key, double... values) {
    return new CommandObject<>(addFlatArgs(commandArguments(TDigestCommand.CDF).key(key), values),
        BuilderFactory.DOUBLE_LIST);
  }

  public final CommandObject<List<Double>> tdigestQuantile(String key, double... quantiles) {
    return new CommandObject<>(addFlatArgs(commandArguments(TDigestCommand.QUANTILE).key(key),
        quantiles), BuilderFactory.DOUBLE_LIST);
  }

  public final CommandObject<Double> tdigestMin(String key) {
    return new CommandObject<>(commandArguments(TDigestCommand.MIN).key(key), BuilderFactory.DOUBLE);
  }

  public final CommandObject<Double> tdigestMax(String key) {
    return new CommandObject<>(commandArguments(TDigestCommand.MAX).key(key), BuilderFactory.DOUBLE);
  }

  public final CommandObject<Double> tdigestTrimmedMean(String key, double lowCutQuantile, double highCutQuantile) {
    return new CommandObject<>(commandArguments(TDigestCommand.TRIMMED_MEAN).key(key).add(lowCutQuantile)
        .add(highCutQuantile), BuilderFactory.DOUBLE);
  }

  public final CommandObject<List<Long>> tdigestRank(String key, double... values) {
    return new CommandObject<>(addFlatArgs(commandArguments(TDigestCommand.RANK).key(key),
        values), BuilderFactory.LONG_LIST);
  }

  public final CommandObject<List<Long>> tdigestRevRank(String key, double... values) {
    return new CommandObject<>(addFlatArgs(commandArguments(TDigestCommand.REVRANK).key(key),
        values), BuilderFactory.LONG_LIST);
  }

  public final CommandObject<List<Double>> tdigestByRank(String key, long... ranks) {
    return new CommandObject<>(addFlatArgs(commandArguments(TDigestCommand.BYRANK).key(key),
        ranks), BuilderFactory.DOUBLE_LIST);
  }

  public final CommandObject<List<Double>> tdigestByRevRank(String key, long... ranks) {
    return new CommandObject<>(addFlatArgs(commandArguments(TDigestCommand.BYREVRANK).key(key),
        ranks), BuilderFactory.DOUBLE_LIST);
  }
  // RedisBloom commands

  // RedisGraph commands
  @Deprecated
  public final CommandObject<List<String>> graphList() {
    return new CommandObject<>(commandArguments(GraphCommand.LIST), BuilderFactory.STRING_LIST);
  }

  @Deprecated
  public final CommandObject<List<String>> graphProfile(String graphName, String query) {
    return new CommandObject<>(commandArguments(GraphCommand.PROFILE).key(graphName).add(query), BuilderFactory.STRING_LIST);
  }

  @Deprecated
  public final CommandObject<List<String>> graphExplain(String graphName, String query) {
    return new CommandObject<>(commandArguments(GraphCommand.EXPLAIN).key(graphName).add(query), BuilderFactory.STRING_LIST);
  }

  @Deprecated
  public final CommandObject<List<List<Object>>> graphSlowlog(String graphName) {
    return new CommandObject<>(commandArguments(GraphCommand.SLOWLOG).key(graphName), BuilderFactory.ENCODED_OBJECT_LIST_LIST);
  }

  @Deprecated
  public final CommandObject<String> graphConfigSet(String configName, Object value) {
    return new CommandObject<>(commandArguments(GraphCommand.CONFIG).add(GraphKeyword.SET).add(configName).add(value), BuilderFactory.STRING);
  }

  @Deprecated
  public final CommandObject<Map<String, Object>> graphConfigGet(String configName) {
    return new CommandObject<>(commandArguments(GraphCommand.CONFIG).add(GraphKeyword.GET).add(configName), BuilderFactory.ENCODED_OBJECT_MAP);
  }
  // RedisGraph commands

  // RedisGears commands
  public final CommandObject<String> tFunctionLoad(String libraryCode, TFunctionLoadParams params) {
    return new CommandObject<>(commandArguments(GearsCommand.TFUNCTION).add(GearsKeyword.LOAD)
        .addParams(params).add(libraryCode), BuilderFactory.STRING);
  }

  public final CommandObject<String> tFunctionDelete(String libraryName) {
    return new CommandObject<>(commandArguments(GearsCommand.TFUNCTION).add(GearsKeyword.DELETE)
        .add(libraryName), BuilderFactory.STRING);
  }

  public final CommandObject<List<GearsLibraryInfo>> tFunctionList(TFunctionListParams params) {
    return new CommandObject<>(commandArguments(GearsCommand.TFUNCTION).add(GearsKeyword.LIST)
        .addParams(params), GearsLibraryInfo.GEARS_LIBRARY_INFO_LIST);
  }

  public final CommandObject<Object> tFunctionCall(String library, String function, List<String> keys, List<String> args) {
    return new CommandObject<>(commandArguments(GearsCommand.TFCALL).add(library + "." + function)
        .add(keys.size()).keys(keys).addObjects(args), BuilderFactory.AGGRESSIVE_ENCODED_OBJECT);
  }

  public final CommandObject<Object> tFunctionCallAsync(String library, String function, List<String> keys, List<String> args) {
    return new CommandObject<>(commandArguments(GearsCommand.TFCALLASYNC).add(library + "." + function)
        .add(keys.size()).keys(keys).addObjects(args), BuilderFactory.AGGRESSIVE_ENCODED_OBJECT);
  }
  // RedisGears commands

  /**
   * Get the instance for JsonObjectMapper if not null, otherwise a new instance reference with
   * default implementation will be created and returned.
   * <p>This process of checking whether or not
   * the instance reference exists follows <a
   * href="https://en.wikipedia.org/wiki/Double-checked_locking#Usage_in_Java"
   * target="_blank">'double-checked lock optimization'</a> approach to reduce the overhead of
   * acquiring a lock by testing the lock criteria (the "lock hint") before acquiring the lock.</p>
   * @return the JsonObjectMapper instance reference
   * @see DefaultGsonObjectMapper
   */
  private JsonObjectMapper getJsonObjectMapper() {
    JsonObjectMapper localRef = this.jsonObjectMapper;
    if (Objects.isNull(localRef)) {
      synchronized (this) {
        localRef = this.jsonObjectMapper;
        if (Objects.isNull(localRef)) {
          this.jsonObjectMapper = localRef = new DefaultGsonObjectMapper();
        }
      }
    }
    return localRef;
  }

  public void setJsonObjectMapper(JsonObjectMapper jsonObjectMapper) {
    this.jsonObjectMapper = jsonObjectMapper;
  }

  public void setDefaultSearchDialect(int dialect) {
    if (dialect == 0) throw new IllegalArgumentException("DIALECT=0 cannot be set.");
    this.searchDialect.set(dialect);
  }

  private class SearchProfileResponseBuilder<T> extends Builder<Map.Entry<T, Map<String, Object>>> {

    private static final String PROFILE_STR = "profile";

    private final Builder<T> replyBuilder;

    public SearchProfileResponseBuilder(Builder<T> replyBuilder) {
      this.replyBuilder = replyBuilder;
    }

    @Override
    public Map.Entry<T, Map<String, Object>> build(Object data) {
      List list = (List) data;
      if (list == null || list.isEmpty()) return null;

      if (list.get(0) instanceof KeyValue) {
        for (KeyValue keyValue : (List<KeyValue>) data) {
          if (PROFILE_STR.equals(BuilderFactory.STRING.build(keyValue.getKey()))) {
            return KeyValue.of(replyBuilder.build(data),
                BuilderFactory.AGGRESSIVE_ENCODED_OBJECT_MAP.build(keyValue.getValue()));
          }
        }
      }

      return KeyValue.of(replyBuilder.build(list.get(0)),
          SearchBuilderFactory.SEARCH_PROFILE_PROFILE.build(list.get(1)));
    }
  }

  private class JsonObjectBuilder<T> extends Builder<T> {

    private final Class<T> clazz;

    public JsonObjectBuilder(Class<T> clazz) {
      this.clazz = clazz;
    }

    @Override
    public T build(Object data) {
      return getJsonObjectMapper().fromJson(BuilderFactory.STRING.build(data), clazz);
    }
  }

  /**
   * {@link JsonObjectBuilder} for {@code Object.class}.
   */
  private final Builder<Object> JSON_GENERIC_OBJECT = new JsonObjectBuilder<>(Object.class);

  private class JsonObjectListBuilder<T> extends Builder<List<T>> {

    private final Class<T> clazz;

    public JsonObjectListBuilder(Class<T> clazz) {
      this.clazz = clazz;
    }

    @Override
    public List<T> build(Object data) {
      if (data == null) {
        return null;
      }
      List<String> list = BuilderFactory.STRING_LIST.build(data);
      return list.stream().map(s -> getJsonObjectMapper().fromJson(s, clazz)).collect(Collectors.toList());
    }
  }

  private static final Builder<Map.Entry<Long, byte[]>> BLOOM_SCANDUMP_RESPONSE = new Builder<Map.Entry<Long, byte[]>>() {
    @Override
    public Map.Entry<Long, byte[]> build(Object data) {
      List<Object> list = (List<Object>) data;
      return new KeyValue<>(BuilderFactory.LONG.build(list.get(0)), BuilderFactory.BINARY.build(list.get(1)));
    }
  };

  private CommandArguments addFlatArgs(CommandArguments args, long... values) {
    for (long value : values) {
      args.add(value);
    }
    return args;
  }

  private CommandArguments addFlatArgs(CommandArguments args, double... values) {
    for (double value : values) {
      args.add(value);
    }
    return args;
  }

  private CommandArguments addFlatKeyValueArgs(CommandArguments args, String... keyvalues) {
    for (int i = 0; i < keyvalues.length; i += 2) {
      args.key(keyvalues[i]).add(keyvalues[i + 1]);
    }
    return args;
  }

  private CommandArguments addFlatKeyValueArgs(CommandArguments args, byte[]... keyvalues) {
    for (int i = 0; i < keyvalues.length; i += 2) {
      args.key(keyvalues[i]).add(keyvalues[i + 1]);
    }
    return args;
  }

  private CommandArguments addFlatMapArgs(CommandArguments args, Map<?, ?> map) {
    for (Map.Entry<? extends Object, ? extends Object> entry : map.entrySet()) {
      args.add(entry.getKey());
      args.add(entry.getValue());
    }
    return args;
  }

  private CommandArguments addSortedSetFlatMapArgs(CommandArguments args, Map<?, Double> map) {
    for (Map.Entry<? extends Object, Double> entry : map.entrySet()) {
      args.add(entry.getValue());
      args.add(entry.getKey());
    }
    return args;
  }

  private CommandArguments addGeoCoordinateFlatMapArgs(CommandArguments args, Map<?, GeoCoordinate> map) {
    for (Map.Entry<? extends Object, GeoCoordinate> entry : map.entrySet()) {
      GeoCoordinate ord = entry.getValue();
      args.add(ord.getLongitude());
      args.add(ord.getLatitude());
      args.add(entry.getKey());
    }
    return args;
  }
}