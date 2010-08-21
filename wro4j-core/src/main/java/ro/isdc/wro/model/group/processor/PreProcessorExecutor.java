package ro.isdc.wro.model.group.processor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.isdc.wro.WroRuntimeException;
import ro.isdc.wro.model.resource.Resource;
import ro.isdc.wro.model.resource.ResourceType;
import ro.isdc.wro.model.resource.factory.UriLocatorFactory;
import ro.isdc.wro.model.resource.locator.UriLocator;
import ro.isdc.wro.model.resource.processor.ResourcePreProcessor;
import ro.isdc.wro.util.encoding.SmartEncodingInputStream;


/**
 * TODO: refactor this class.
 * Apply all preProcessor on provided {@link Resource} and returns the result of execution as String.
 * <p>
 * This is useful when you want to preProcess a resource which is not a part of the model (css import use-case).
 *
 * @author Alex Objelean
 */
public abstract class PreProcessorExecutor {
  private static final Logger LOG = LoggerFactory.getLogger(PreProcessorExecutor.class);


  /**
   * Apply preProcessors on resources and merge them.
   *
   * @param resources what are the resources to merge.
   * @param minimize whether minimize aware processors must be applied or not.
   * @return preProcessed merged content.
   * @throws IOException if IO error occurs while merging.
   */
  public String processAndMerge(final List<Resource> resources, final boolean minimize)
    throws IOException {
    final StringBuffer result = new StringBuffer();
    for (final Resource resource : resources) {
      LOG.debug("merging resource: " + resource);
      result.append(execute(resource, minimize));
    }
    return result.toString();
  }


  /**
   * Execute all the preProcessors on the provided resource.
   *
   * @param resource {@link Resource} to preProcess.
   * @param minimize whether the minimimize aware preProcessor must be applied.
   * @return the result of preProcessing as string content.
   * @throws IOException if {@link Resource} cannot be found or any other related errors.
   */
  private String execute(final Resource resource, final boolean minimize)
    throws IOException {
    if (resource == null) {
      throw new IllegalArgumentException("Resource cannot be null!");
    }
    // merge preProcessorsBy type and anyPreProcessors
    final Collection<ResourcePreProcessor> processors = getPreProcessorsByType(resource.getType());
    processors.addAll(getPreProcessorsByType(null));
    if (!minimize) {
      GroupsProcessorImpl.removeMinimizeAwareProcessors(processors);
    }
    return applyPreProcessors(processors, resource);
  }


  /**
   * Apply a list of preprocessors on a resource.
   *
   * @throws IOException if any IO error occurs while processing.
   */
  private String applyPreProcessors(final Collection<ResourcePreProcessor> processors, final Resource resource)
    throws IOException {
    // get original content
    Reader reader = null;
    Writer output = new StringWriter();
    try {
      reader = getResourceReader(resource);
    } catch (final IOException e) {
      LOG.debug("IgnoreMissingResources: " + ignoreMissingResources());
      if (ignoreMissingResources()) {
        LOG.warn("Invalid resource found: " + resource);
        return output.toString();
      } else {
        LOG.warn("Invalid resource found: " + resource + ". Cannot continue processing. IgnoreMissingResources is + "
          + ignoreMissingResources());
        throw e;
      }
    }
    if (processors.isEmpty()) {
      IOUtils.copy(reader, output);
      return output.toString();
    }
    for (final ResourcePreProcessor processor : processors) {
      output = new StringWriter();
      LOG.debug("applying preProcessor: " + processor.getClass().getName());
      processor.process(resource, reader, output);
      reader = new StringReader(output.toString());
    }
    return output.toString();
  }


  /**
   * @param resource {@link Resource} for which a Reader should be returned.
   * @return {@link Reader} for the resource.
   * @throws IOException if there was a problem retrieving a reader.
   * @throws WroRuntimeException if {@link Reader} is null.
   */
  private Reader getResourceReader(final Resource resource)
    throws IOException {
    Reader reader = null;
    final UriLocator locator = getUriLocatorFactory().getInstance(resource.getUri());
    if (locator != null) {
      final InputStream is = locator.locate(resource.getUri());
      // wrap reader with bufferedReader for top efficiency
      reader = new BufferedReader(new InputStreamReader(new SmartEncodingInputStream(is)));
    }
    if (reader == null) {
      // TODO skip invalid resource, instead of throwing exception
      throw new IOException("Exception while retrieving InputStream from uri: " + resource.getUri());
    }
    return reader;
  }

  /**
   * @return true if the missing resources should be ignored.
   */
  protected abstract boolean ignoreMissingResources();

  /**
   * TODO document.
   * @param resourceType
   * @return
   */
  protected abstract Collection<ResourcePreProcessor> getPreProcessorsByType(ResourceType resourceType);

  /**
   * @return {@link UriLocatorFactory}.
   */
  protected abstract UriLocatorFactory getUriLocatorFactory();

}