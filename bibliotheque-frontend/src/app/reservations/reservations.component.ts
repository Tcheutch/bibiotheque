import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Books } from '../_model/books';
import { Reservation, ReservationAffichage, ReservationRequest, ReservationStatus } from '../_model/reservation';
import { Users } from '../_model/users';
import { ReservationService } from '../_service/reservation.service';
import { UserAuthService } from '../_service/user-auth.service';

@Component({
  selector: 'app-reservations',
  templateUrl: './reservations.component.html',
  styleUrls: ['./reservations.component.css']
})
export class ReservationsComponent implements OnInit {

  reservations: Reservation[] = [];
  livres: Books[] = [];
  adherents: Users[] = [];
  statutFiltre?: ReservationStatus;

  // Faux pour un Adhérent (User) : pilote le masquage du sélecteur "Adhérent"
  // (formulaire) et l'absence d'appel à GET /admin/users ci-dessous, pas
  // seulement l'affichage — cet endpoint est hasRole('Admin') et un compte
  // User recevrait un 403 rien qu'en le chargeant.
  estBibliothecaire = false;

  chargement = false;
  erreur: string | null = null;

  // Transmis en @Input au formulaire ; incrémenté uniquement après un 201
  // confirmé, pour déclencher sa réinitialisation (jamais au clic lui-même).
  resetFormulaire = 0;

  // Refus de création (400/404/409), affichés à côté du formulaire.
  erreurCreation: string | null = null;
  erreursChampsCreation: { [champ: string]: string } | null = null;

  // Refus d'annulation (409 RG-05/RG-06), affiché au-dessus de la liste.
  erreurAnnulation: string | null = null;

  constructor(
    private reservationService: ReservationService,
    private userAuthService: UserAuthService,
  ) { }

  ngOnInit(): void {
    this.estBibliothecaire = this.estUtilisateurBibliothecaire();
    this.chargerListesDeReference();
    this.chargerReservations();
  }

  private estUtilisateurBibliothecaire(): boolean {
    const roles: any = this.userAuthService.getRoles();
    return !!roles && roles.some((role: any) => role.roleName === 'Admin');
  }

  get reservationsAffichables(): ReservationAffichage[] {
    return this.reservations.map(reservation => ({
      ...reservation,
      titreLivre: this.libelleLivre(reservation.livreId),
      nomAdherent: this.libelleAdherent(reservation.adherentId),
    }));
  }

  private chargerListesDeReference() {
    this.reservationService.listerLivres().subscribe(data => this.livres = data);
    // RS-04 : seul un Bibliothécaire choisit l'adhérent, un Adhérent réserve
    // pour lui-même. GET /admin/users est hasRole('Admin') — jamais appelé
    // pour un User, pas seulement masqué côté formulaire.
    if (this.estBibliothecaire) {
      this.reservationService.listerAdherents().subscribe(data => this.adherents = data);
    }
  }

  chargerReservations() {
    this.chargement = true;
    this.erreur = null;
    this.reservationService.listerReservations(this.statutFiltre).subscribe({
      next: (data) => {
        this.reservations = data;
        this.chargement = false;
      },
      error: (erreur: HttpErrorResponse) => {
        this.erreur = this.extraireMessageErreur(erreur);
        this.chargement = false;
      },
    });
  }

  onFiltreChange(statut?: ReservationStatus) {
    this.statutFiltre = statut;
    this.chargerReservations();
  }

  onCreerReservation(request: ReservationRequest) {
    this.erreurCreation = null;
    this.erreursChampsCreation = null;
    this.reservationService.creerReservation(request).subscribe({
      next: () => {
        this.chargerReservations();
        this.resetFormulaire++;
      },
      error: (erreur: HttpErrorResponse) => {
        this.erreurCreation = this.extraireMessageErreur(erreur);
        this.erreursChampsCreation = this.extraireErreursChamps(erreur);
      },
    });
  }

  onAnnulerReservation(id: number) {
    this.erreurAnnulation = null;
    this.reservationService.annulerReservation(id).subscribe({
      next: () => this.chargerReservations(),
      // Amélioration au-delà du texte strict du sujet (qui ne mandate un
      // rafraîchissement qu'au succès) : un refus d'annulation (RG-05/06,
      // le plus souvent une course avec une autre session) révèle presque
      // toujours que l'état affiché est périmé — on recharge donc aussi
      // ici pour que la ligne concernée s'auto-corrige, quel que soit le
      // code. La création n'a pas ce problème : un refus de création ne
      // rend périmée aucune ligne déjà affichée, donc n'y est pas touchée.
      error: (erreur: HttpErrorResponse) => {
        this.erreurAnnulation = this.extraireMessageErreur(erreur);
        this.chargerReservations();
      },
    });
  }

  private libelleLivre(livreId: number): string {
    const livre = this.livres.find(l => l.bookId === livreId);
    return livre ? livre.bookName : `Livre #${livreId}`;
  }

  private libelleAdherent(adherentId: number): string {
    const adherent = this.adherents.find(a => a.userId === adherentId);
    return adherent ? adherent.name : `Adhérent #${adherentId}`;
  }

  // Reprend le message métier du serveur tel quel (ApiError.message). Le
  // message de repli ne sert que si le corps est absent ou inexploitable
  // (ex. serveur injoignable) : formulé pour ne pas ressembler à un message
  // métier, afin que les deux restent distinguables à l'écran.
  private extraireMessageErreur(erreur: HttpErrorResponse): string {
    if (erreur.error && typeof erreur.error === 'object' && erreur.error.message) {
      return erreur.error.message;
    }
    return "Le serveur est injoignable. Vérifiez qu'il est démarré, puis réessayez.";
  }

  // Détail par champ (ApiError.errors), présent uniquement pour un 400 de
  // validation. Vide pour un 404/409 : .message seul suffit dans ce cas.
  private extraireErreursChamps(erreur: HttpErrorResponse): { [champ: string]: string } | null {
    const erreurs = erreur.error && typeof erreur.error === 'object' ? erreur.error.errors : null;
    if (erreurs && typeof erreurs === 'object' && Object.keys(erreurs).length > 0) {
      return erreurs;
    }
    return null;
  }
}
