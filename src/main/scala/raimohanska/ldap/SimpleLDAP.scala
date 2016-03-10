package raimohanska.ldap

import java.io.{FileInputStream, InputStream}

import org.apache.directory.server.core.DefaultDirectoryService
import org.apache.directory.server.core.entry.DefaultServerEntry
import org.apache.directory.server.ldap.LdapServer
import org.apache.directory.server.protocol.shared.transport.TcpTransport
import org.apache.directory.shared.ldap.exception.{LdapConfigurationException, LdapNameAlreadyBoundException}
import org.apache.directory.shared.ldap.ldif.{LdifEntry, LdifReader}
import org.slf4j.LoggerFactory

object SimpleLDAP extends App {
    Config.parseCommandLine(args) match {
      case Some(config) => {
        try {
          val server = new SimpleLDAP(config.port)
          config.ldifFiles.foreach { filename =>
            server.importLdif(new FileInputStream(filename))
          }
        } catch {
          case e: Exception =>
            e.printStackTrace
            System.exit(1)
          }
        }
      case None => System.exit(1)
    }
}

case class Config(port: Int = 10389, ldifFiles: List[String] = Nil)

object Config {
  def parseCommandLine(args: Array[String]) = new scopt.OptionParser[Config]("ldapserver") {
    opt[Int]('p', "port") action { (p,c) => c.copy(port = p)}
    opt[String]('f', "ldiffile") action { (f, c) => c.copy(ldifFiles = c.ldifFiles ++ List(f))}
  }.parse(args, Config())
}

class SimpleLDAP(port: Int) {
  private val logger = LoggerFactory.getLogger(getClass)
  def admin = service.getAdminSession

  private val service = new DefaultDirectoryService();
  service.startup();

  private val ldapServer = new LdapServer();
  ldapServer.setDirectoryService( service );
  ldapServer.setAllowAnonymousAccess( false );
  private val ldapTransport = new TcpTransport( port );
  ldapServer.setTransports( ldapTransport );
  ldapServer.start();

  def importLdif( in: InputStream )
  {
    try
    {
      val session = service.getAdminSession

      val iterator = new LdifReader( in );

      while ( iterator.hasNext )
      {
        val entry: LdifEntry = iterator.next();
        try {
          session.add(
            new DefaultServerEntry(
              service.getRegistries, entry.getEntry() ) );
          logger.info("Added: " + entry.getDn)
        } catch {
          case e: LdapNameAlreadyBoundException =>
            logger.info("Already exists: " + entry.getDn)
        }
      }
    }
    catch
      {
        case e: Exception =>
          val msg = "failed while trying to parse system ldif file";
          val ne = new LdapConfigurationException( msg );
          ne.setRootCause( e );
          throw ne;
      }
  }
}