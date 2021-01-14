/*
 * Copyright 2000-2021 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.queueManager.settings;

import jetbrains.buildServer.serverSide.CustomSettings;
import jetbrains.buildServer.serverSide.CustomSettingsManager;
import jetbrains.buildServer.serverSide.SettingsMap;
import jetbrains.buildServer.util.Dates;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created with IntelliJ IDEA.
 *
 * @author Oleg Rybak (oleg.rybak@jetbrains.com)
 */
public class SettingsManagerImpl implements SettingsManager {

  interface FIELDS {
    String QUEUE_ENABLED = "queue-enabled";
    String CHANGED_BY = "state-changed-by";
    String CHANGED_ON = "state-changed-on";
    String CHANGED_REASON = "state-changed-reason";
    String CHANGED_BY_ACTOR = "state-changed-actor";
  }

  private static final Map<String, String> DEFAULTS;
  static {
    final Map<String, String> defaults = new HashMap<>();
    // queue is enabled by default
    defaults.put(FIELDS.QUEUE_ENABLED, Boolean.toString(Boolean.TRUE));
    defaults.put(FIELDS.CHANGED_ON, Long.toString(Dates.makeDate(2012, 12, 12).getTime())); // 12.12.2012 0:0:0  for ease of testing
    defaults.put(FIELDS.CHANGED_BY_ACTOR, Actor.USER.name());
    DEFAULTS = Collections.unmodifiableMap(defaults);
  }

  private static final String DEFAULT_VALUE = "";

  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock(true);

  private final SettingsMap mySettingsMap;

  public SettingsManagerImpl(@NotNull final PluginDescriptor pluginDescriptor,
                             @NotNull final CustomSettingsManager customSettingsManager) {
    final CustomSettings settings = customSettingsManager.getCustomSettings(pluginDescriptor);
    mySettingsMap = settings.getGlobalSettings();
  }

  @Override
  public void setQueueEnabled(boolean enabled) {
    writeValue(FIELDS.QUEUE_ENABLED, Boolean.toString(enabled));
  }

  @Override
  public boolean isQueueEnabled() {
    boolean result;
    try {
      myLock.readLock().lock();
      final String val = readValueWithDefault(FIELDS.QUEUE_ENABLED);
      result =  Boolean.valueOf(val);
    } finally {
      myLock.readLock().unlock();
    }
    return result;
  }

  @Nullable
  @Override
  public Long getQueueStateChangedBy() {
    Long result = null;
    try {
      myLock.readLock().lock();
      final String val = readValueWithDefault(FIELDS.CHANGED_BY);
      if (!DEFAULT_VALUE.equals(val)) {
        result = Long.parseLong(val);
      }
    } finally {
      myLock.readLock().unlock();
    }
    return result;
  }

  @Override
  public void setQueueStateChangedBy(@Nullable final Long userId) {
      writeValue(FIELDS.CHANGED_BY, userId != null ? Long.toString(userId) : DEFAULT_VALUE);
  }

  @NotNull
  public Date getQueueStateChangedOn() {
    Date result;
    try {
      myLock.readLock().lock();
      final String val = readValueWithDefault(FIELDS.CHANGED_ON);
      result = new Date(Long.parseLong(val));
    } finally {
      myLock.readLock().unlock();
    }
    return result;
  }

  @Override
  public void setQueueStateChangedOn(@NotNull final Date date) {
      writeValue(FIELDS.CHANGED_ON, Long.toString(date.getTime()));
  }


  @NotNull
  @Override
  public String getQueueStateChangedReason() {
    String result;
    try {
      myLock.readLock().lock();
      result = readValueWithDefault(FIELDS.CHANGED_REASON);
    } finally {
      myLock.readLock().unlock();
    }
    return result;
  }

  @Override
  public void setQueueStateChangedReason(@NotNull final String reason) {
    writeValue(FIELDS.CHANGED_REASON, reason);
  }

  @NotNull
  @Override
  public Actor getQueueStateChangedActor() {
    try {
      myLock.readLock().lock();
      return Actor.valueOf(readValueWithDefault(FIELDS.CHANGED_BY_ACTOR));
    } catch(IllegalArgumentException e) {
      return Actor.valueOf(DEFAULTS.get(FIELDS.CHANGED_BY_ACTOR));
    } finally {
      myLock.readLock().unlock();
    }
  }

  @Override
  public void setQueueStateChangedActor(@NotNull final Actor actor) {
    writeValue(FIELDS.CHANGED_BY_ACTOR, actor.name());
  }

  /**
   * Reads value from storage.
   * <b>Should be called under read lock</b>, as it promotes it to write lock
   * to write default value
   *
   * @param key key for the value
   * @return value from storage. If value is absent, returns {@code default} value.
   * If default value is absent, returns {@code empty string}.
   */
  @NotNull
  private String readValueWithDefault(@NotNull final String key) {
    String result = mySettingsMap.getValue(key);
    if (result == null) {
      try {
        myLock.readLock().unlock();
        myLock.writeLock().lock();
        result = DEFAULTS.get(key);
        if (result == null) {
          result = DEFAULT_VALUE;
        }
        mySettingsMap.setValue(key, result);
      } finally {
        myLock.readLock().lock();
        myLock.writeLock().unlock();
      }
    }
    return result;
  }

  /**
   * Writes value to storage. Manages locks by itself
   * @param key key to store value under
   * @param value value to store
   */
  private void writeValue(@NotNull final String key, @NotNull final String value) {
    try {
      myLock.writeLock().lock();
      mySettingsMap.setValue(key, value);
    } finally {
      myLock.writeLock().unlock();
    }
  }
}
