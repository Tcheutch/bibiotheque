import { HttpErrorResponse, HttpEvent, HttpHandler, HttpInterceptor, HttpRequest } from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError } from 'rxjs/operators';
import { Observable, throwError } from 'rxjs';
import { UserAuthService } from '../_service/user-auth.service';
import { Injectable } from '@angular/core';

const MESSAGE_SESSION_PAR_DEFAUT = "Votre session n'est plus valide. Veuillez vous reconnecter.";

@Injectable()
export class AuthInterceptor implements HttpInterceptor {
  constructor(
    private userAuthService: UserAuthService,
    private router:Router
  ) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    if (req.headers.get('No-Auth') === 'True') {
      return next.handle(req.clone());
    }

    const token = this.userAuthService.getToken();

    req = this.addToken(req, token);

    return next.handle(req).pipe(
        catchError(
            (err:HttpErrorResponse) => {
                console.log(err.status);
                if(err.status === 401) {
                    // Token absent, invalide ou expiré : la session locale n'est plus
                    // utilisable. On la vide (sinon AuthGuard laisserait encore passer)
                    // et on transmet à /login le message du serveur tel quel
                    // (ex. "Votre session a expiré..."), pour qu'il y soit affiché.
                    this.userAuthService.clear();
                    this.router.navigate(['/login'], { state: { message: this.messageSession(err) } });
                } else if(err.status === 403) {
                    this.router.navigate(['/forbidden']);
                }
                // Propage l'erreur HTTP d'origine (corps + statut) au lieu d'un message
                // générique : les écrans qui affichent le message métier du serveur
                // (ex. l'écran Réservation) ont besoin du HttpErrorResponse d'origine.
                return throwError(err);
            }
        )
    );
  }

  private messageSession(err: HttpErrorResponse): string {
      const message = err.error?.message;
      return typeof message === 'string' && message.trim() !== ''
          ? message
          : MESSAGE_SESSION_PAR_DEFAUT;
  }

  private addToken(request:HttpRequest<any>, token:string) {
      return request.clone(
          {
              setHeaders: {
                  Authorization : `Bearer ${token}`
              }
          }
      );
  }
}