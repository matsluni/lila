package controllers

import lila.app._
import views._
import lila.security.Permission
import lila.user.{ Context, User ⇒ UserModel, UserRepo }
import lila.user.tube.userTube
import lila.db.api.$find
import lila.common.LilaCookie

import play.api.mvc._, Results._

object User extends LilaController {

  private def env = Env.user
  private def gamePaginator = Env.game.paginator
  private def forms = lila.user.DataForm

  def show(username: String) = Open { implicit ctx ⇒
    isXhr.fold(mini(username), filter(username, "all", 1))
  }

  def showFilter(username: String, filterName: String, page: Int) = Open { implicit ctx ⇒
    filter(username, filterName, page)
  }

  private def filter(username: String, filterName: String, page: Int)(implicit ctx: Context) =
    Reasonable(page) {
      OptionFuOk(UserRepo named username) { userShow(_, filterName, page) }
    }

  private def userShow(u: UserModel, filterName: String, page: Int)(implicit ctx: Context) =
    (u.enabled || isGranted(_.UserSpy)).fold({
      for {
        info ← Env.current.userInfo(u, ctx)
        filters = mashup.GameFilterMenu(info, ctx.me, filterName)
        pag ← (filters.query.fold(Env.bookmark.api.gamePaginatorByUser(u, page)) { query ⇒
          gamePaginator.recentlyCreated(query, filters.cachedNb)(page)
        })
      } yield html.user.show(u, info, pag, filters)
    }, fuccess(html.user.disabled(u)))

  private def mini(username: String)(implicit ctx: Context) =
    OptionOk(UserRepo named username) { user ⇒
      Thread sleep 200
      html.user.mini(user)
    }

  def list(page: Int) = Open { implicit ctx ⇒
    Reasonable(page) {
      onlineUsers zip env.paginator.elo(page) map {
        case (users, pag) ⇒ html.user.list(pag, users)
      }
    }
  }

  def mod(username: String) = Secure(_.UserSpy) { implicit ctx ⇒
    me ⇒ OptionFuOk(UserRepo named username) { user ⇒
      Env.security userSpy user.id map { spy ⇒
        html.user.mod(user, spy)
      }
    }
  }

  def opponents(username: String) = Open { implicit ctx ⇒
    OptionFuOk(UserRepo named username) { user ⇒
      lila.game.BestOpponents(user.id, 50) map { ops ⇒
        html.user.opponents(user, ops)
      }
    }
  }

  def online = Open { implicit ctx ⇒
    onlineUsers map { html.user.online(_) }
  }

  def autocomplete = Open { implicit ctx ⇒
    get("term", ctx.req).filter(""!=).fold(BadRequest("No search term provided").fuccess: Fu[Result]) { term ⇒
      JsonOk(UserRepo usernamesLike term)
    }
  }

  def setBio = AuthBody { ctx ⇒
    me ⇒
      implicit val req = ctx.body
      forms.bio.bindFromRequest.fold(
        f ⇒ fulogwarn(f.errors.toString) inject ~me.bio,
        bio ⇒ UserRepo.setBio(me.id, bio) inject bio
      ) map { Ok(_) }
  }

  def passwd = Auth { implicit ctx ⇒
    me ⇒
      Ok(html.user.passwd(me, forms.passwd)).fuccess
  }

  def passwdApply = AuthBody { implicit ctx ⇒
    me ⇒
      implicit val req = ctx.body
      FormFuResult(forms.passwd) { err ⇒
        fuccess(html.user.passwd(me, err))
      } { passwd ⇒
        for {
          ok ← UserRepo.checkPassword(me.id, passwd.oldPasswd)
          _ ← ok ?? UserRepo.passwd(me.id, passwd.newPasswd1)
        } yield ok.fold(
          Redirect(routes.User show me.username),
          BadRequest(html.user.passwd(me, forms.passwd))
        )
      }
  }

  def close = Auth { implicit ctx ⇒
    me ⇒
      Ok(html.user.close(me)).fuccess
  }

  def closeConfirm = Auth { ctx ⇒
    me ⇒
      implicit val req = ctx.req
      (UserRepo disable me.id) >>
        Env.team.api.quitAll(me.id) >>
        (Env.security disconnect me.id) inject {
          Redirect(routes.User show me.username) withCookies LilaCookie.newSession
        }
  }

  def export(username: String) = Open { implicit ctx ⇒
    OptionFuResult(UserRepo named username) { u ⇒
      Env.game export u map { Redirect(_) }
    }
  }

  private def onlineUsers: Fu[List[UserModel]] = 
    $find byIds env.onlineUserIdMemo.keys map {
      _ sortBy (-_.elo)
    }
}
