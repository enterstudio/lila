package controllers

import akka.pattern.ask
import play.api.data._, Forms._
import play.api.libs.concurrent.Akka
import play.api.libs.iteratee._
import play.api.libs.json._
import play.api.mvc._, Results._

import lila.app._
import lila.common.HTTPRequest
import lila.hub.actorApi.captcha.ValidCaptcha
import makeTimeout.large
import views._

object Main extends LilaController {

  private lazy val blindForm = Form(tuple(
    "enable" -> nonEmptyText,
    "redirect" -> nonEmptyText
  ))

  def toggleBlindMode = OpenBody { implicit ctx =>
    implicit val req = ctx.body
    fuccess {
      blindForm.bindFromRequest.fold(
        err => BadRequest, {
          case (enable, redirect) =>
            Redirect(redirect) withCookies lila.common.LilaCookie.cookie(
              Env.api.Accessibility.blindCookieName,
              if (enable == "0") "" else Env.api.Accessibility.hash,
              maxAge = Env.api.Accessibility.blindCookieMaxAge.some,
              httpOnly = true.some)
        })
    }
  }

  def websocket = SocketOption { implicit ctx =>
    get("sri") ?? { uid =>
      Env.site.socketHandler(uid, ctx.userId, get("flag")) map some
    }
  }

  def captchaCheck(id: String) = Open { implicit ctx =>
    Env.hub.actor.captcher ? ValidCaptcha(id, ~get("solution")) map {
      case valid: Boolean => Ok(valid fold (1, 0))
    }
  }

  def embed = Action { req =>
    Ok {
      s"""document.write("<iframe src='${Env.api.Net.BaseUrl}?embed=" + document.domain + "' class='lichess-iframe' allowtransparency='true' frameBorder='0' style='width: ${getInt("w", req) | 820}px; height: ${getInt("h", req) | 650}px;' title='Lichess free online chess'></iframe>");"""
    } as JAVASCRIPT withHeaders (CACHE_CONTROL -> "max-age=86400")
  }

  def developers = Open { implicit ctx =>
    fuccess {
      html.site.developers()
    }
  }

  def irc = Open { implicit ctx =>
    ctx.me ?? Env.team.api.mine map {
      html.site.irc(_)
    }
  }

  def themepicker = Open { implicit ctx =>
    fuccess {
      html.base.themepicker()
    }
  }

  def mobile = Open { implicit ctx =>
    OptionOk(Prismic oneShotBookmark "mobile-apk") {
      case (doc, resolver) => html.mobile.home(doc, resolver)
    }
  }

  def jslog = Open { ctx =>
    val referer = HTTPRequest.referer(ctx.req)
    loginfo(s"[jslog] ${ctx.req.remoteAddress} ${ctx.userId} $referer")
    ctx.userId.?? {
      Env.report.api.autoBotReport(_, referer)
    }
  }

  def csp = Action(BodyParsers.parse.tolerantJson) { req =>
    import lila.common.PimpedJson._
    req.body obj "csp-report" foreach { report =>
      val gameUrl = report str "document-uri"
      val blockedUrl = report str "blocked-uri"
      val status = report int "status-code"
      loginfo(s"[csp-report] ${req.remoteAddress} ${~status} $blockedUrl on $gameUrl")
    }
    Ok
  }
}
