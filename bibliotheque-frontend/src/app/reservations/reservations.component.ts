import { Component, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Books } from '../_model/books';
import { Reservation, ReservationAffichage, ReservationRequest, ReservationStatus } from '../_model/reservation';
import { Users } from '../_model/users';
import { ReservationService } from '../_service/reservation.service';

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

  chargement = false;
  erreur: string | null = null;

  // Transmis en @Input au formulaire ; incrémenté uniquement après un 201
  // confirmé, pour déclencher sa réinitialisation (jamais au clic lui-même).
  resetFormulaire = 0;

  constructor(private reservationService: ReservationService) { }

  ngOnInit(): void {
    this.chargerListesDeReference();
    this.chargerReservations();
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
    this.reservationService.listerAdherents().subscribe(data => this.adherents = data);
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
    this.reservationService.creerReservation(request).subscribe({
      next: () => {
        this.chargerReservations();
        this.resetFormulaire++;
      },
      // Phase 7 : affichage du message métier (400/404/409) à côté du
      // formulaire. Pour l'instant, un échec ne réinitialise pas le
      // formulaire (la sélection de l'utilisateur reste intacte).
      error: () => {},
    });
  }

  onAnnulerReservation(id: number) {
    this.reservationService.annulerReservation(id).subscribe(() => {
      this.chargerReservations();
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
}
