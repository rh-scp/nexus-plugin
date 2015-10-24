/*
 * Copyright (c) 2007-2014 Sonatype, Inc. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.redhat.scp.pipdl;

import java.util.List;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.proxy.IllegalOperationException;
import org.sonatype.nexus.proxy.ItemNotFoundException;
import org.sonatype.nexus.proxy.ResourceStoreRequest;
import org.sonatype.nexus.proxy.access.Action;
import org.sonatype.nexus.proxy.item.StorageFileItem;
import org.sonatype.nexus.proxy.item.StorageItem;
import org.sonatype.nexus.proxy.repository.ProxyRepository;
import org.sonatype.nexus.proxy.repository.Repository;
import org.sonatype.nexus.proxy.repository.RequestStrategy;
import org.sonatype.sisu.goodies.common.ComponentSupport;
import org.sonatype.sisu.goodies.eventbus.EventBus;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.HttpResponse;

/**
 * PIPDL interface {@link org.sonatype.nexus.proxy.repository.RequestStrategy}.
 *
 * @since 1.0
 */
@Named(PIPDLRequestProcessor.ID)
public class PIPDLRequestProcessor
    extends ComponentSupport
    implements RequestStrategy
{
  public static final String ID = "pipdl";

  private final EventBus eventBus;

  private final List<Pipdl> pipdls;

  @Inject
  public PIPDLRequestProcessor(final EventBus eventBus,
                               final List<Pipdl> pipdls)
  {
    this.eventBus = Preconditions.checkNotNull(eventBus);
    this.pipdls = Preconditions.checkNotNull(pipdls);

    if (pipdls.isEmpty()) {
      log.warn("No PIDPL components detected");
    }
    else if (log.isDebugEnabled()) {
      log.debug("PIPDLS:");
      for (Pipdl pipdl : pipdls) {
        log.debug("  {}", pipdl);
      }
    }
  }

  @VisibleForTesting
  boolean enqueueJob(final StorageFileItem item) {
    log.debug("Enqueueing PIPDL job: {}", item.getPath());
    boolean done = true;

    for (Pipdl pipdl : pipdls) {
      if (pipdl.enqueueJob(item)) {
        eventBus.post(new PipdlEvent(item.getRepositoryItemUid().getRepository(), item));
      } else {
        done = false;
        // error, retry?
      }
    }

    DefaultHttpClient hc = new DefaultHttpClient();
    try {
      hc.execute(new HttpGet(String.format("http://queue/new?item=%s&hash=%s", item.getPath(), "not-so-random-for-now")));
    }
    catch (IOException e) {
      log.info("Error enqueing job: {}", e);
    }

    return done;
  }

  //    @Override
  //    public boolean process(final Repository repository, final ResourceStoreRequest request, final Action action) {
  //        // don't decide until have content
  //        return true;
  //    }
  //
  //    @Override
  //    public boolean shouldProxy(final ProxyRepository repository, final ResourceStoreRequest request) {
  //        // don't decide until have content
  //        return true;
  //    }
  //
  //    @Override
  //    public boolean shouldRetrieve(final Repository repository, final ResourceStoreRequest request, final StorageItem item)
  //        throws IllegalOperationException, ItemNotFoundException, AccessDeniedException
  //    {
  //        // don't decide until have content
  //        return true;
  //    }
  //
  //    @Override
  //    public boolean shouldCache(final ProxyRepository repository, final AbstractStorageItem item) {
  //        if (item instanceof StorageFileItem) {
  //            StorageFileItem file = (StorageFileItem) item;
  //            return !hasVirus(file);
  //        }
  //        else {
  //            return true;
  //        }
  //    }

  @Override
  public void onHandle(final Repository repository, final ResourceStoreRequest resourceStoreRequest,
                       final Action action)
      throws ItemNotFoundException, IllegalOperationException
  {
  }

  @Override
  public void onServing(final Repository repository, final ResourceStoreRequest resourceStoreRequest,
                        final StorageItem storageItem)
      throws ItemNotFoundException, IllegalOperationException
  {
    if (storageItem instanceof StorageFileItem) {
      StorageFileItem file = (StorageFileItem) storageItem;
      if (!enqueueJob(file)) {
        throw new IllegalStateException("Error enqueueing job");
      }
    }
  }

  @Override
  public void onRemoteAccess(final ProxyRepository proxyRepository, final ResourceStoreRequest resourceStoreRequest,
                             final StorageItem storageItem)
      throws ItemNotFoundException, IllegalOperationException
  {
  }
}
