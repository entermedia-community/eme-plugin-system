package org.openedit.generators;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Path;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import gg.jte.CodeResolver;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.TemplateNotFoundException;
import gg.jte.output.WriterOutput;
import gg.jte.resolve.DirectoryCodeResolver;
import org.openedit.Generator;
import org.openedit.OpenEditException;
import org.openedit.OpenEditRuntimeException;
import org.openedit.WebPageRequest;
import org.openedit.error.ContentNotAvailableException;
import org.openedit.page.Page;
import org.openedit.page.manage.PageManager;
import org.openedit.util.OutputFiller;

/**
 * Generator that renders {@code .jte} templates using the JTE template engine.
 *
 * <p>
 * This generator is designed to run <em>alongside</em> the existing
 * {@link VelocityGenerator}: {@code .html} pages continue to be handled by
 * Velocity while new or migrated {@code .jte} templates are handled here.
 *
 * <p>
 * Each JTE template must declare a single parameter of type
 * {@link JtePageContext}:
 * 
 * <pre>
 *   {@literal @}param org.openedit.generators.JtePageContext context
 * </pre>
 *
 * <p>
 * Spring wiring requires {@code rootDirectory} and {@code pageManager}.
 */
public class JteGenerator extends BaseGenerator implements Generator {
  public static final Log log = LogFactory.getLog(JteGenerator.class);

  private File fieldRootDirectory;
  private PageManager fieldPageManager;
  private TemplateEngine fieldEngine;
  private CodeResolver fieldCodeResolver;

  public CodeResolver getCodeResolver() {
    if (fieldCodeResolver == null) {
      fieldCodeResolver = new DirectoryCodeResolver(getRootDirectory().toPath());
    }
    return fieldCodeResolver;
  }

  public void setCodeResolver(CodeResolver inCodeResolver) {
    fieldCodeResolver = inCodeResolver;
  }

  public JteGenerator() {
  }

  // -------------------------------------------------------------------------
  // Generator contract
  // -------------------------------------------------------------------------

  @Override
  public void generate(WebPageRequest inContext, Page inPage, Output inOut)
      throws OpenEditException {

    if (log.isDebugEnabled()) {
      log.debug("JTE rendering " + inPage);
    }

    if (!inPage.exists()) {
      String vir = inPage.get("virtual");
      if (!Boolean.parseBoolean(vir)) {
        log.info("Missing: " + inPage.getPath());
        throw new ContentNotAvailableException("Missing: " + inPage.getPath(), inPage.getPath());
      }
      return;
    }

    if (inPage.isBinary()) {
      try {
        InputStream in = inPage.getInputStream();
        new OutputFiller().fill(in, inContext.getOutputStream());
      } catch (IOException ex) {
        throw new OpenEditException(ex.getMessage() + " on " + inPage.getPath(), ex);
      }
      return;
    }
    String templatePath = inPage.getContentItem().getPath();
    try {
      JtePageContext context = new JtePageContext(inContext.getPageMap());
      Writer writer = inOut.getWriter();
      // DirectoryCodeResolver.resolve() uses Path.resolve(name); if name starts
      // with '/' Java treats it as an absolute path, bypassing the root.

      // String templatePath = inPage.getPath();
      if (templatePath.startsWith("/")) {
        templatePath = templatePath.substring(1);
      }
      // templatePath = getCodeResolver().resolve(templatePath);
      WriterOutput writerOutput = new WriterOutput(writer);
      getEngine().render(templatePath, context, writerOutput);
      writer.flush();
    } catch (TemplateNotFoundException ex) {
      throw new ContentNotAvailableException(ex.getMessage() + " on " + templatePath, templatePath);
    } catch (Exception ex) {
      if (ignoreError(ex)) {
        log.debug("Browser canceled request");
        return;
      }
      if (ex instanceof OpenEditException) {
        throw (OpenEditException) ex;
      }
      throw new OpenEditException(ex.getMessage() + " on " + templatePath, ex);
    }
  }

  // -------------------------------------------------------------------------
  // Engine lifecycle
  // -------------------------------------------------------------------------

  /**
   * Lazily initialises the JTE {@link TemplateEngine}.
   *
   * <p>
   * Compiled template classes are written to
   * {@code <rootDirectory>/WEB-INF/jte-classes/} so they survive hot-reload
   * without re-compiling on every request.
   */
  public TemplateEngine getEngine() {
    if (fieldEngine == null) {
      fieldEngine = createEngine();
    }
    return fieldEngine;
  }

  private TemplateEngine createEngine() {
    try {
      Path root = getRootDirectory().toPath();
      Path classesDir = root.resolve("WEB-INF/jte-classes");
      classesDir.toFile().mkdirs();

      DirectoryCodeResolver codeResolver = new DirectoryCodeResolver(root);
      return TemplateEngine.create(codeResolver, classesDir, ContentType.Html);
    } catch (Exception ex) {
      throw new OpenEditRuntimeException("Failed to initialise JTE TemplateEngine", ex);
    }
  }

  // -------------------------------------------------------------------------
  // Spring-injected properties
  // -------------------------------------------------------------------------

  public File getRootDirectory() {
    return fieldRootDirectory;
  }

  public void setRootDirectory(File inRootDirectory) {
    fieldRootDirectory = inRootDirectory;
  }

  public PageManager getPageManager() {
    return fieldPageManager;
  }

  public void setPageManager(PageManager inPageManager) {
    fieldPageManager = inPageManager;
  }
}
