package com.project.tracking_system.service.user;

import org.springframework.stereotype.Service;


@Service
public class HtmlEmailTemplateService {

    public String generateConfirmationEmail(String confirmationCode) {
        return "<!DOCTYPE html>" +
                "<html lang=\"ru\">" +
                "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<title>Подтверждение регистрации</title>" +
                "<style>" +
                "body {" +
                "    font-family: Arial, sans-serif;" +
                "    background-color: #f4f4f4;" +
                "    color: #333333;" +
                "    margin: 0;" +
                "    padding: 0;" +
                "}" +
                ".container {" +
                "    width: 100%;" +
                "    padding: 20px;" +
                "    background-color: #ffffff;" +
                "    box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);" +
                "    max-width: 600px;" +
                "    margin: 50px auto;" +
                "}" +
                ".header {" +
                "    background-color: #4CAF50;" +
                "    padding: 10px;" +
                "    text-align: center;" +
                "    color: #ffffff;" +
                "    border-top-left-radius: 8px;" +
                "    border-top-right-radius: 8px;" +
                "}" +
                ".header h1 {" +
                "    margin: 0;" +
                "    font-size: 24px;" +
                "}" +
                ".content {" +
                "    padding: 20px;" +
                "    text-align: center;" +
                "}" +
                ".content p {" +
                "    font-size: 16px;" +
                "    margin-bottom: 20px;" +
                "}" +
                ".confirmation-code {" +
                "    font-size: 24px;" +
                "    font-weight: bold;" +
                "    color: #4CAF50;" +
                "    padding: 10px;" +
                "    border-radius: 5px;" +
                "    background-color: #f9f9f9;" +
                "    display: inline-block;" +
                "    margin: 20px 0;" +
                "}" +
                ".footer {" +
                "    background-color: #f9f9f9;" +
                "    padding: 10px;" +
                "    text-align: center;" +
                "    color: #888888;" +
                "    border-bottom-left-radius: 8px;" +
                "    border-bottom-right-radius: 8px;" +
                "}" +
                ".footer p {" +
                "    font-size: 12px;" +
                "    margin: 0;" +
                "}" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class=\"container\">" +
                "<div class=\"header\">" +
                "<h1>Подтверждение регистрации</h1>" +
                "</div>" +
                "<div class=\"content\">" +
                "<p>Здравствуйте!</p>" +
                "<p>Вы получили это письмо, потому что ваш адрес электронной почты был использован для регистрации на нашем сайте.</p>" +
                "<p>Пожалуйста, введите следующий код для подтверждения вашей регистрации:</p>" +
                "<div class=\"confirmation-code\">" + confirmationCode + "</div>" +
                "<p>Если вы не регистрировались на нашем сайте, просто проигнорируйте это письмо.</p>" +
                "</div>" +
                "<div class=\"footer\">" +
                "<p>С уважением, Команда нашего сайта</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }

    public String generatePasswordResetEmail(String resetLink) {
        return "<!DOCTYPE html>" +
                "<html lang=\"ru\">" +
                "<head>" +
                "<meta charset=\"UTF-8\">" +
                "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">" +
                "<title>Восстановление пароля</title>" +
                "<style>" +
                "body {" +
                "    font-family: Arial, sans-serif;" +
                "    background-color: #f4f4f4;" +
                "    color: #333333;" +
                "    margin: 0;" +
                "    padding: 0;" +
                "}" +
                ".container {" +
                "    width: 100%;" +
                "    padding: 20px;" +
                "    background-color: #ffffff;" +
                "    box-shadow: 0 0 10px rgba(0, 0, 0, 0.1);" +
                "    max-width: 600px;" +
                "    margin: 50px auto;" +
                "}" +
                ".header {" +
                "    background-color: #4CAF50;" +
                "    padding: 10px;" +
                "    text-align: center;" +
                "    color: #ffffff;" +
                "    border-top-left-radius: 8px;" +
                "    border-top-right-radius: 8px;" +
                "}" +
                ".header h1 {" +
                "    margin: 0;" +
                "    font-size: 24px;" +
                "}" +
                ".content {" +
                "    padding: 20px;" +
                "    text-align: center;" +
                "}" +
                ".content p {" +
                "    font-size: 16px;" +
                "    margin-bottom: 20px;" +
                "}" +
                ".reset-link {" +
                "    font-size: 18px;" +
                "    font-weight: bold;" +
                "    color: #ffffff;" +
                "    background-color: #4CAF50;" +
                "    padding: 10px 20px;" +
                "    border-radius: 5px;" +
                "    text-decoration: none;" +
                "    display: inline-block;" +
                "    margin: 20px 0;" +
                "}" +
                ".footer {" +
                "    background-color: #f9f9f9;" +
                "    padding: 10px;" +
                "    text-align: center;" +
                "    color: #888888;" +
                "    border-bottom-left-radius: 8px;" +
                "    border-bottom-right-radius: 8px;" +
                "}" +
                ".footer p {" +
                "    font-size: 12px;" +
                "    margin: 0;" +
                "}" +
                "</style>" +
                "</head>" +
                "<body>" +
                "<div class=\"container\">" +
                "<div class=\"header\">" +
                "<h1>Восстановление пароля</h1>" +
                "</div>" +
                "<div class=\"content\">" +
                "<p>Здравствуйте!</p>" +
                "<p>Вы получили это письмо, потому что запросили восстановление пароля на нашем сайте.</p>" +
                "<p>Чтобы установить новый пароль, нажмите на кнопку ниже:</p>" +
                "<a href=\"" + resetLink + "\" class=\"reset-link\">Сбросить пароль</a>" +
                "<p>Если вы не запрашивали восстановление пароля, просто проигнорируйте это письмо.</p>" +
                "</div>" +
                "<div class=\"footer\">" +
                "<p>С уважением, Команда нашего сайта</p>" +
                "</div>" +
                "</div>" +
                "</body>" +
                "</html>";
    }


}