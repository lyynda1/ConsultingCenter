import com.advisora.utils.MailConfig;
public class CheckMailConfig {
  public static void main(String[] args) {
    System.out.println("host=" + MailConfig.smtpHost());
    System.out.println("port=" + MailConfig.smtpPort());
    String user = MailConfig.username();
    String pass = MailConfig.password();
    System.out.println("user_present=" + (user != null && !user.isBlank()));
    System.out.println("user=" + user);
    System.out.println("pass_present=" + (pass != null && !pass.isBlank()));
    System.out.println("pass_len=" + (pass == null ? -1 : pass.length()));
  }
}
