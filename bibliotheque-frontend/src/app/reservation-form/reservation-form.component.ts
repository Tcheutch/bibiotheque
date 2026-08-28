import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { Books } from '../_model/books';
import { ReservationRequest } from '../_model/reservation';
import { Users } from '../_model/users';

@Component({
  selector: 'app-reservation-form',
  templateUrl: './reservation-form.component.html',
  styleUrls: ['./reservation-form.component.css']
})
export class ReservationFormComponent implements OnChanges {

  @Input() livres: Books[] = [];
  @Input() adherents: Users[] = [];
  // Incrémenté par le conteneur uniquement après un 201 confirmé par le
  // serveur : c'est ce qui déclenche la réinitialisation, jamais le clic
  // sur Réserver lui-même (sinon un 409/400/404 effacerait la sélection
  // de l'utilisateur avant même qu'il voie le message d'erreur).
  @Input() reinitialiser = 0;

  @Output() creer = new EventEmitter<ReservationRequest>();

  livreId?: number;
  adherentId?: number;
  succesVisible = false;

  ngOnChanges(changes: SimpleChanges) {
    if (changes['reinitialiser'] && !changes['reinitialiser'].firstChange) {
      this.livreId = undefined;
      this.adherentId = undefined;
      this.succesVisible = true;
    }
  }

  onLivreIdChange(livreId?: number) {
    this.livreId = livreId;
    this.succesVisible = false;
  }

  onAdherentIdChange(adherentId?: number) {
    this.adherentId = adherentId;
    this.succesVisible = false;
  }

  onSubmit() {
    if (!this.livreId || !this.adherentId) {
      return;
    }
    this.creer.emit({ livreId: this.livreId, adherentId: this.adherentId });
  }
}
